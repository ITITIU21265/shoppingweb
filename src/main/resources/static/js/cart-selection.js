document.addEventListener("DOMContentLoaded", () => {
  const checkboxes = Array.from(document.querySelectorAll(".cart-select-checkbox"));
  const selectedItemsCount = document.getElementById("selected-items-count");
  const selectedSubtotal = document.getElementById("selected-subtotal");
  const submitButton = document.getElementById("checkout-selection-submit");

  if (checkboxes.length === 0 || !selectedItemsCount || !selectedSubtotal || !submitButton) {
    return;
  }

  const formatCurrency = (amount) => `$${amount.toFixed(2)}`;

  const updateSummary = () => {
    const selected = checkboxes.filter((checkbox) => checkbox.checked);

    const totalQuantity = selected.reduce((sum, checkbox) => {
      const quantity = Number.parseInt(checkbox.dataset.quantity || "0", 10);
      return sum + (Number.isNaN(quantity) ? 0 : quantity);
    }, 0);

    const subtotal = selected.reduce((sum, checkbox) => {
      const lineTotal = Number.parseFloat(checkbox.dataset.lineTotal || "0");
      return sum + (Number.isNaN(lineTotal) ? 0 : lineTotal);
    }, 0);

    selectedItemsCount.textContent = String(totalQuantity);
    selectedSubtotal.textContent = formatCurrency(subtotal);
    submitButton.disabled = selected.length === 0;
    submitButton.textContent = selected.length === 0
      ? "Select items to checkout"
      : `Proceed to checkout (${selected.length})`;
  };

  checkboxes.forEach((checkbox) => {
    checkbox.addEventListener("change", updateSummary);
  });

  updateSummary();
});
