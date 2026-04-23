(function () {
    function formatJson(output) {
        if (!output || output.getAttribute("data-formatted") === "true") {
            return;
        }
        var raw = output.getAttribute("data-raw-json") || "";
        var formatted = raw;
        try {
            formatted = JSON.stringify(JSON.parse(raw), null, 2);
        } catch (ignore) {
            formatted = raw || "-";
        }
        output.textContent = formatted;
        output.setAttribute("data-formatted", "true");
    }

    document.addEventListener("toggle", function (event) {
        var details = event.target;
        if (!details || !details.classList || !details.classList.contains("json-expand-details") || !details.open) {
            return;
        }
        formatJson(details.querySelector(".json-formatted-output"));
    }, true);
}());
