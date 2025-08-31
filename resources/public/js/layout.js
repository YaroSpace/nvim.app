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

function hideAlert() {
  setTimeout(() => {
    document.getElementById("alert-box").classList.add("hidden");
  }, 3000);
}

document.addEventListener("DOMContentLoaded", () => {
  hideAlert();

  document
    .getElementById("menu-btn")
    .addEventListener("click", (event) => event.stopPropagation());
});

document.addEventListener("htmx:oobAfterSwap", () => {
  hideAlert();
});

document.addEventListener("keydown", (event) => {
  const tagName = event.target.tagName.toLowerCase();

  if (tagName === "input" || tagName === "textarea") {
    return;
  }

  switch (event.key) {
    case "j":
      window.scrollBy(0, 100);
      break;
    case "k":
      window.scrollBy(0, -100);
      break;
    case "h":
      htmx.trigger("#btn-previous", "click");
      break;
    case "l":
      htmx.trigger("#btn-next", "click");
      break;
  }
});
