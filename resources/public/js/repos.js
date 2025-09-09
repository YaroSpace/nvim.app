function showPreview(el) {
  const preview = el.closest("form").querySelector(".preview-popup");

  preview.classList.remove("hidden");

  if (preview.querySelector(".preview-content") == null) {
    htmx.trigger(preview, "click");
  }
}

function hidePreview(el) {
  el.closest("form").querySelector(".preview-popup").classList.add("hidden");
}
