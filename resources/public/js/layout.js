document.addEventListener("DOMContentLoaded", () => {
  document
    .getElementById("menu-btn")
    .addEventListener("click", (event) => event.stopPropagation());
});

function toggleMenu() {
  const menu = document.getElementById("menu");
  menu.classList.toggle("hidden");

  const hideMenu = (event) => {
    if (!menu.contains(event.target)) {
      menu.classList.add("hidden");
      document.removeEventListener("click", hideMenu);
    }
  };

  document.addEventListener("click", hideMenu);
}
