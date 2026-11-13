renderUnifiedSidebar('profile');

(function () {
  const methods = {
    ZALOPAY: {name: 'ZaloPay', image: 'zalopay.png'},
    MOMO: {name: 'MoMo', image: 'momo.png'},
    SHOPEEPAY: {name: 'ShopeePay', image: 'shopeepay.png'},
    BANK: {name: 'Techcombank', image: 'bank.png'}
  };
  const query = new URLSearchParams(location.search);
  const requestedPlan = (query.get('plan') || 'PRO').toUpperCase();
  const code = ['PREMIUM', 'VIP'].includes(requestedPlan) ? 'VIP' : 'PRO';
  const byId = id => document.getElementById(id);
  const months = byId('subscriptionMonths');
  const openButton = byId('submitSubscription');
  const confirmButton = byId('confirmPaymentMethod');
  const closeButton = byId('closePaymentMethods');
  const options = byId('paymentMethodOptions');
  const dialog = byId('paymentMethodsDialog');
  const error = byId('paymentMethodError');
  let sequence = 0;
  let quoteReady = false;
  let submitting = false;
  let pendingTransactionId = null;
  let quotedAmount;
  let transferAccount;
  const note = byId('paymentNote');
  const hint = dialog.querySelector('.payment-method-hint');
  const originalHint = hint.textContent;
  months.value = ['1', '3', '12'].includes(query.get('months')) ? query.get('months') : '1';

  function selectedMethod() {
    return options.querySelector('input:checked').value;
  }

  function showQr() {
    const method = methods[selectedMethod()];
    const path = 'images/payment/' + method.image;
    byId('paymentQr').src = path;
    byId('paymentQr').alt = 'Mã QR nhận tiền ' + method.name;
    byId('downloadPaymentQr').href = path;
    byId('openPaymentQr').href = path;
  }

  async function quote() {
    const seq = ++sequence;
    quoteReady = false;
    openButton.disabled = true;
    try {
      const response = await fetch('/api/billing/quote?plan=' + code + '&months=' + months.value);
      if (!response.ok) throw Error();
      const value = await response.json();
      if (seq !== sequence) return;
      const amount = value.total.toLocaleString('vi-VN') + 'đ';
      byId('planName').textContent = code;
      byId('paymentDialogPlan').textContent = code;
      byId('planPrice').textContent = amount + ' / ' + value.months + ' tháng';
      byId('transferPrice').textContent = amount;
      const hint = byId('planOriginalPrice');
      hint.classList.remove('hidden', 'line-through', 'text-slate-400');
      hint.classList.add('text-slate-600');
      hint.textContent = value.monthlyPrice.toLocaleString('vi-VN') + 'đ/tháng';
      let user = {};
      try { user = JSON.parse(localStorage.getItem('user') || '{}'); } catch (_) {}
      quotedAmount = value.total;
      transferAccount = user.username || 'USER';
      quoteReady = true;
      openButton.disabled = false;
    } catch (_) {
      if (seq === sequence) byId('planPrice').textContent = 'Chưa thể tải giá. Vui lòng tải lại trang.';
    }
  }

  openButton.addEventListener('click', async () => {
    if (!quoteReady || openButton.disabled) return;
    openButton.disabled = true;
    const seq = sequence;
    try {
      const response = await fetch('/api/payments/mine/latest', {cache: 'no-store'});
      if (!response.ok) throw Error('Chưa thể kiểm tra yêu cầu thanh toán hiện tại.');
      const latest = response.status === 200 ? await response.json() : null;
      if (seq !== sequence) return;
      const pending = latest?.status === 'PENDING' ? latest : null;
      pendingTransactionId = pending?.transactionId ?? null;
      hint.textContent = pending
        ? 'Chỉ quét mã nếu bạn chưa chuyển khoản. Sau khi chuyển, nhập mã giao dịch hoặc ghi chú để bổ sung vào yêu cầu đang chờ.'
        : originalHint;
      if (pending) {
        if (!Number.isSafeInteger(pendingTransactionId) || !Number.isFinite(pending.amount) || ![1, 3, 12].includes(pending.months)) {
          throw Error('Chưa tải được số tiền của yêu cầu đang chờ. Vui lòng tải lại trang.');
        }
      }
      const paymentPlan = pending?.plan ?? code;
      const paymentMonths = pending?.months ?? months.value;
      const paymentAmount = pending?.amount ?? quotedAmount;
      byId('paymentDialogPlan').textContent = paymentPlan;
      byId('transferPrice').textContent = paymentAmount.toLocaleString('vi-VN') + 'đ';
      byId('transferSyntax').textContent = 'UPGRADE ' + paymentPlan + ' ' + paymentMonths + 'TH ' + transferAccount;
      error.hidden = true;
      showQr();
      dialog.showModal();
    } catch (failure) {
      AppModal.alert(failure.message, {tone: 'error'});
    } finally {
      if (seq === sequence) openButton.disabled = !quoteReady;
    }
  });

  confirmButton.addEventListener('click', async () => {
    if (!quoteReady || submitting) return;
    const body = {
      planId: code,
      months: months.value,
      paymentMethod: selectedMethod(),
      paymentNote: note.value.trim()
    };
    if (pendingTransactionId !== null) body.transactionId = String(pendingTransactionId);
    submitting = true;
    note.disabled = true;
    options.disabled = true;
    confirmButton.disabled = true;
    closeButton.disabled = true;
    error.hidden = true;
    try {
      const response = await fetch('/api/payments/checkout', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(body)
      });
      const payment = await response.json().catch(() => null);
      if (!response.ok) throw Error(payment?.error);
      dialog.close();
      window.AccountAlerts.waitForPayment(payment);
    } catch (failure) {
      error.textContent = failure.message || 'Chưa thể gửi yêu cầu. Vui lòng thử lại.';
      error.hidden = false;
    } finally {
      submitting = false;
      note.disabled = false;
      options.disabled = false;
      confirmButton.disabled = false;
      closeButton.disabled = false;
    }
  });

  closeButton.addEventListener('click', () => dialog.close());
  dialog.addEventListener('cancel', event => { if (submitting) event.preventDefault(); });
  options.addEventListener('change', showQr);
  months.addEventListener('change', quote);
  quote().then(() => {
    if (query.get('checkout') === '1' && quoteReady) openButton.click();
  });
  window.lucide?.createIcons?.();
})();
