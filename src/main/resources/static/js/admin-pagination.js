// Paginate rendered records while preserving the current page on refresh.
const adminPageState = new WeakMap();
function paginateAdminRows(body) {
  const rows = Array.from(body.rows).filter(row => row.cells.length > 1);
  const pageSize = 5;
  let state = adminPageState.get(body);
  if (!state) {
    const nav = document.createElement("nav");
    nav.setAttribute("aria-label", "Phân trang");
    nav.className = "flex items-center justify-center gap-4 py-4";
    const previous = document.createElement("button");
    const next = document.createElement("button");
    const summary = document.createElement("span");
    summary.setAttribute("aria-live", "polite");
    previous.textContent = "Trang trước";
    next.textContent = "Trang sau";
    for (const button of [previous, next]) {
      button.type = "button";
      button.className = "px-3 py-2 rounded-lg border disabled:opacity-40";
    }
    state = {page: 0, nav, previous, next, summary};
    previous.onclick = () => { state.page--; paginateAdminRows(body); };
    next.onclick = () => { state.page++; paginateAdminRows(body); };
    nav.append(previous, summary, next);
    body.closest("table").after(nav);
    adminPageState.set(body, state);
  }
  const total = Math.max(1, Math.ceil(rows.length / pageSize));
  state.page = Math.min(Math.max(0, state.page), total - 1);
  rows.forEach((row, index) => { row.hidden = Math.floor(index / pageSize) !== state.page; });
  state.nav.style.display = total > 1 ? "" : "none";
  state.previous.disabled = state.page === 0;
  state.next.disabled = state.page === total - 1;
  state.summary.textContent = "Trang " + (state.page + 1) + " / " + total + " · " + rows.length + " bản ghi";
}

function compareAdminNewest(first, second) {
  const firstTime = Date.parse(first.createdAt) || 0;
  const secondTime = Date.parse(second.createdAt) || 0;
  if (firstTime !== secondTime) {
    return secondTime - firstTime;
  }
  return Number(second.id) - Number(first.id);
}
