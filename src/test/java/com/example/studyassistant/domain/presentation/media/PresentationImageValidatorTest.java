package com.example.studyassistant.domain.presentation.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresentationImageValidatorTest {
    private final PresentationImageValidator validator = new PresentationImageValidator();

    @Test
    void A_validPngIsDecoded() throws Exception {
        var info = validator.validate("fixture.png", "image/png", pngBytes().length, new ByteArrayInputStream(pngBytes()));
        assertThat(info.mimeType()).isEqualTo("image/png");
        assertThat(info.width()).isEqualTo(3);
        assertThat(info.height()).isEqualTo(2);
    }

    @Test
    void B_validJpegIsDecoded() throws Exception {
        byte[] jpeg = jpegBytes();
        var info = validator.validate("fixture.jpeg", "image/jpeg", jpeg.length, new ByteArrayInputStream(jpeg));
        assertThat(info.mimeType()).isEqualTo("image/jpeg");
        assertThat(info.width()).isEqualTo(3);
        assertThat(info.height()).isEqualTo(2);
    }

    @Test
    void C_webpIsDecodedByImageIo() throws Exception {
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA==");
        var info = validator.validate("fixture.webp", "image/webp", webp.length, new ByteArrayInputStream(webp));
        assertThat(info.mimeType()).isEqualTo("image/webp");
        assertThat(info.width()).isEqualTo(1);
        assertThat(info.height()).isEqualTo(1);
    }

    @Test
    void D_extensionAndClientMimeSpoofingAreRejected() throws Exception {
        byte[] png = pngBytes();
        assertThatThrownBy(() -> validator.validate("fake.jpg", "image/jpeg", png.length, new ByteArrayInputStream(png)))
                .isInstanceOf(PresentationImageValidator.InvalidPresentationImageException.class);
        assertThatThrownBy(() -> validator.validate("real.png", "text/html", png.length, new ByteArrayInputStream(png)))
                .isInstanceOf(PresentationImageValidator.InvalidPresentationImageException.class);
    }

    @Test
    void E_svgIsRejected() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> validator.validate("vector.svg", "image/svg+xml", svg.length, new ByteArrayInputStream(svg)))
                .isInstanceOf(PresentationImageValidator.InvalidPresentationImageException.class);
    }

    @Test
    void F_fileLargerThanTenMiBIsRejectedBeforeDecode() {
        assertThatThrownBy(() -> validator.validate("large.png", "image/png",
                PresentationImageValidator.MAX_FILE_BYTES + 1, new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(PresentationImageValidator.InvalidPresentationImageException.class);
    }

    @Test
    void G_widthOver8192IsRejectedFromImageHeader() throws Exception {
        assertDimensionRejected(pngHeader(8193, 2));
    }

    @Test
    void H_heightOver8192IsRejectedFromImageHeader() throws Exception {
        assertDimensionRejected(pngHeader(2, 8193));
    }

    @Test
    void I_moreThanFortyMillionPixelsIsRejectedFromImageHeader() throws Exception {
        assertDimensionRejected(pngHeader(8192, 5000));
    }

    @Test
    void J_emptyAndCorruptImageBytesAreRejected() throws Exception {
        assertThatThrownBy(() -> validator.validate("empty.png", "image/png", 0, new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(PresentationImageValidator.InvalidPresentationImageException.class);
        byte[] corrupt = new byte[]{1, 2, 3, 4, 5, 6};
        assertThatThrownBy(() -> validator.validate("corrupt.png", "image/png", corrupt.length, new ByteArrayInputStream(corrupt)))
                .isInstanceOf(PresentationImageValidator.InvalidPresentationImageException.class);
    }

    @Test
    void V_pathLikeFilenameCannotControlStorageKey(@TempDir Path root) throws Exception {
        LocalMediaStorage storage = new LocalMediaStorage(root.toString());
        MediaStorage.StagedMedia staged = storage.stage(new ByteArrayInputStream(pngBytes()), PresentationImageValidator.MAX_FILE_BYTES);
        String serverKey = staged.key();
        try {
            assertThat(serverKey).matches("[0-9a-f-]{36}");
            assertThat(serverKey).doesNotContain("traversal").doesNotContain("/").doesNotContain("\\");
            assertThat(validator.validate("teacher.png", "image/png", staged.sizeBytes(), storage.openStaged(staged)).mimeType())
                    .isEqualTo("image/png");
        } finally {
            storage.discard(staged);
        }
    }

    private void assertDimensionRejected(byte[] pngHeader) {
        assertThatThrownBy(() -> validator.validate("dimensions.png", "image/png", pngHeader.length, new ByteArrayInputStream(pngHeader)))
                .isInstanceOf(PresentationImageValidator.InvalidPresentationImageException.class)
                .hasMessageContaining("dimensions");
    }

    private byte[] pngBytes() throws IOException {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
        return output.toByteArray();
    }

    private byte[] jpegBytes() throws IOException {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x336699);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpeg", output)) throw new IOException("JPEG writer unavailable");
        return output.toByteArray();
    }

    private byte[] pngHeader(int width, int height) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        data.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        data.writeInt(13);
        byte[] chunkType = "IHDR".getBytes(StandardCharsets.US_ASCII);
        data.write(chunkType);
        ByteArrayOutputStream chunkData = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(chunkData);
        header.writeInt(width);
        header.writeInt(height);
        header.writeByte(8);
        header.writeByte(2);
        header.writeByte(0);
        header.writeByte(0);
        header.writeByte(0);
        byte[] ihdr = chunkData.toByteArray();
        data.write(ihdr);
        CRC32 crc = new CRC32();
        crc.update(chunkType);
        crc.update(ihdr);
        data.writeInt((int) crc.getValue());
        data.flush();
        return output.toByteArray();
    }
}
