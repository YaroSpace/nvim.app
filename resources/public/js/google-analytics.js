window.dataLayer = window.dataLayer || [];

GID = "G-ZL4M33MPVW";

function gtag() {
  dataLayer.push(arguments);
}

htmx.on("htmx:afterSwap", function (event) {
  gtag("config", GID, {
    page_path: event.detail.pathInfo.finalRequestPath,
  });
});

gtag("js", new Date());
gtag("config", GID);
