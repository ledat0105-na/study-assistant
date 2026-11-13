(function () {
  let currentUser;
  let waiting;
  let dismissed;
  let busy = false;
  let dialog;
  let lastAdminCounts;
  let lastNotice;
  const checkoutPage = location.pathname.endsWith("/payment.html");

  function showPending(payment, openDialog = false) {
    waiting = payment.transactionId;
    if (!openDialog || !checkoutPage || dismissed === waiting) return;
    if (!dialog) {
      dialog = document.createElement("dialog");
      dialog.className = "rounded-2xl p-6 max-w-sm shadow-xl";
      dialog.setAttribute("aria-labelledby", "paymentWaitingTitle");
      dialog.innerHTML = '<h2 id="paymentWaitingTitle" class="text-lg font-bold">Đang chờ duyệt nâng cấp</h2>'
        + '<div class="my-3 flex gap-3 items-start"><span aria-hidden="true" class="animate-spin inline-block w-4 h-4 rounded-full border-2 border-indigo-200 border-t-indigo-700"></span><p></p></div>'
        + '<button type="button" class="rounded-lg border px-3 py-2">Đóng và tiếp tục</button>';
      dialog.querySelector("button").onclick = () => dialog.close();
      dialog.addEventListener("close", () => { if (waiting != null) dismissed = waiting; });
      document.body.append(dialog);
    }
    dialog.querySelector("p").textContent = "Yêu cầu gói " + payment.plan + " đang chờ quản trị viên đối chiếu. Bạn có thể đóng cửa sổ và tiếp tục sử dụng hệ thống.";
    if (!dialog.open) dialog.showModal();
  }

  function showBanner(message, link, label) {
    let banner = document.getElementById("account-alert-banner");
    if (!banner) {
      banner = document.createElement("aside");
      banner.id = "account-alert-banner";
      banner.setAttribute("role", "status");
      banner.className = "fixed right-4 bottom-4 z-50 rounded-xl shadow-lg bg-white text-slate-900 border border-indigo-200 p-4 max-w-sm";
      document.body.append(banner);
    }
    const text = document.createElement("p");
    text.textContent = message;
    const dismiss = document.createElement("button");
    dismiss.type = "button";
    dismiss.textContent = "Đóng";
    dismiss.className = "ml-3 underline";
    dismiss.onclick = () => banner.remove();
    banner.replaceChildren(text);
    if (link) {
      const action = document.createElement("a");
      action.href = link;
      action.textContent = label;
      action.className = "text-indigo-700 underline";
      banner.append(action);
    }
    banner.append(dismiss);
  }

  async function checkAdmin() {
    const response = await fetch("/api/admin/alerts", {cache: "no-store"});
    if (!response.ok) return;
    const counts = await response.json();
    const failedDocuments = location.pathname.endsWith("/admin-documents.html") ? 0 : counts.failedDocuments;
    const key = [counts.pendingPayments, failedDocuments, counts.serverErrors].join(":");
    if (key === lastAdminCounts) return;
    lastAdminCounts = key;
    const messages = [];
    if (counts.pendingPayments) messages.push(counts.pendingPayments + " yêu cầu gói chờ duyệt");
    if (failedDocuments) messages.push(failedDocuments + " tài liệu xử lý thất bại");
    if (counts.serverErrors) messages.push(counts.serverErrors + " lỗi máy chủ");
    if (!messages.length) {
      document.getElementById("account-alert-banner")?.remove();
      return;
    }
    const link = counts.pendingPayments ? "admin-payments.html" : failedDocuments ? "admin-documents.html?status=FAILED" : null;
    showBanner(messages.join(" · "), link, counts.pendingPayments ? "Mở danh sách duyệt" : "Xem tài liệu lỗi");
  }

  async function checkPayment() {
    const requestedWhileWaiting = waiting;
    const response = await fetch("/api/payments/mine/latest", {cache: "no-store"});
    if (response.status !== 200) return;
    const payment = await response.json();
    if (requestedWhileWaiting !== waiting && payment.transactionId !== waiting) return;
    if (payment.status === "PENDING") {
      showPending(payment);
      return;
    }
    const wasWaiting = waiting === payment.transactionId;
    waiting = undefined;
    if (dialog?.open) dialog.close();
    const key = "payment-notice:" + currentUser.id;
    const notice = String(payment.transactionId) + ":" + payment.status;
    if (lastNotice === notice || localStorage.getItem(key) === notice) return;
    lastNotice = notice;
    localStorage.setItem(key, notice);
    if (payment.status === "APPROVED") {
      void window.BillingUI?.refresh();
      if (wasWaiting && checkoutPage && window.AppModal) {
        void AppModal.alert("Đã kích hoạt thành công gói " + payment.plan + ".", {tone: "success"});
      } else {
        showBanner("Yêu cầu đã được duyệt. Gói " + payment.plan + " đã được kích hoạt.", "profile.html", "Xem gói");
      }
    } else if (payment.status === "REJECTED") {
      showBanner("Yêu cầu nâng cấp gói " + payment.plan + " đã bị từ chối.", null, null);
    }
  }

  async function check() {
    if (!currentUser || busy || document.hidden) return;
    busy = true;
    try {
      if (currentUser.role === "ADMIN") await checkAdmin();
      else await checkPayment();
    } catch (_) {
      // Transient polling failures are retried at the next interval.
    } finally {
      busy = false;
    }
  }

  window.AccountAlerts = {waitForPayment(payment) {
    dismissed = undefined;
    showPending(payment, true);
    void check();
  }};
  document.addEventListener("DOMContentLoaded", () => {
    if (/\/(?:login|register|index)\.html$/.test(location.pathname) || location.pathname === "/") return;
    try { currentUser = JSON.parse(localStorage.getItem("user") || "null"); } catch (_) { return; }
    if (!currentUser?.id) return;
    void check();
    window.setInterval(check, 3000);
    document.addEventListener("visibilitychange", check);
  });
})();
