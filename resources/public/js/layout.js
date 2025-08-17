document.addEventListener("DOMContentLoaded", () => {
  document
    .getElementById("menu-btn")
    .addEventListener("click", (event) => event.stopPropagation());
});

function toggleMenu(button) {
  const menu = button.nextElementSibling;
  menu.classList.toggle("hidden");

  document.addEventListener("click", function hideMenu(event) {
    if (!menu.contains(event.target) && event.target !== button) {
      if (!menu.classList.contains("hidden")) {
        menu.classList.add("hidden");
      }
      document.removeEventListener("click", hideMenu);
    }
  });
}
