window.dataLayer = window.dataLayer || [];

GID = "G-ZL4M33MPVW";

function gtag() {
  dataLayer.push(arguments);
}

gtag("js", new Date());
gtag("config", GID);

htmx.on("htmx:afterSwap", function (event) {
  gtag("event", "search", {
    search_term: event.detail.pathInfo.finalRequestPath,
  });
});
