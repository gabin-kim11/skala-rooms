document.addEventListener("DOMContentLoaded", () => {
  const form = document.querySelector("#bookingForm");
  if (!form) return;

  const roomInput = document.querySelector("#bookingRoomId");
  const startInput = document.querySelector("#bookingStart");
  const endInput = document.querySelector("#bookingEnd");
  const selectionText = document.querySelector("#selectionText");
  const selectionDuration = document.querySelector("#selectionDuration");
  const submitButton = document.querySelector("#bookingSubmit");
  const tabs = [...document.querySelectorAll(".room-tabs button")];
  const grids = [...document.querySelectorAll(".time-grid")];
  const maxSlots = Number(form.dataset.maxSlots || 0);
  let activeRoomId = roomInput.value;
  let selected = [];

  function resetSelection() {
    selected = [];
    document.querySelectorAll(".time-grid button.selected").forEach(button => button.classList.remove("selected"));
    startInput.value = "";
    endInput.value = "";
    selectionText.textContent = "시간을 선택해주세요.";
    selectionDuration.textContent = maxSlots ? "최대 3시간" : "오늘 예약 한도 도달";
    submitButton.disabled = true;
  }

  function updateSelection() {
    const buttons = selected.map(index => document.querySelector(`.time-grid[data-room-id="${activeRoomId}"] button[data-index="${index}"]`));
    document.querySelectorAll(".time-grid button.selected").forEach(button => button.classList.remove("selected"));
    buttons.forEach(button => button.classList.add("selected"));
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    startInput.value = first.dataset.start;
    endInput.value = last.dataset.end;
    selectionText.textContent = `${first.dataset.start}~${last.dataset.end}`;
    selectionDuration.textContent = `${selected.length * 30}분`;
    submitButton.disabled = false;
  }

  tabs.forEach(tab => tab.addEventListener("click", () => {
    activeRoomId = tab.dataset.roomId;
    roomInput.value = activeRoomId;
    tabs.forEach(item => item.classList.toggle("active", item === tab));
    grids.forEach(grid => grid.classList.toggle("active", grid.dataset.roomId === activeRoomId));
    resetSelection();
  }));

  grids.forEach(grid => grid.querySelectorAll("button:not(:disabled)").forEach(button => {
    button.addEventListener("click", () => {
      if (maxSlots < 1) return;
      const index = Number(button.dataset.index);
      if (!selected.length) {
        selected = [index];
      } else if (selected.includes(index)) {
        if (index === selected[0]) selected.shift();
        else if (index === selected[selected.length - 1]) selected.pop();
        else selected = [index];
      } else if (index === selected[selected.length - 1] + 1 && selected.length < maxSlots) {
        selected.push(index);
      } else if (index === selected[0] - 1 && selected.length < maxSlots) {
        selected.unshift(index);
      } else {
        selected = [index];
      }
      selected.length ? updateSelection() : resetSelection();
    });
  }));

  document.querySelector("#bookingDate")?.addEventListener("change", event => {
    window.location.href = `/home?scheduleDate=${encodeURIComponent(event.target.value)}#booking`;
  });

  form.addEventListener("submit", event => {
    if (!startInput.value || !endInput.value) event.preventDefault();
  });
});
