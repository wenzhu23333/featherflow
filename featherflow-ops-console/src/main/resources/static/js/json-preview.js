(function () {
    function findParent(element, className) {
        var current = element;
        while (current && current !== document) {
            if (current.classList && current.classList.contains(className)) {
                return current;
            }
            current = current.parentNode;
        }
        return null;
    }

    function openDialog(button) {
        var widget = findParent(button, "json-preview-widget");
        var dialog = widget ? widget.querySelector(".json-preview-dialog") : null;
        if (!dialog) {
            return;
        }
        formatModalBody(dialog.querySelector(".json-modal-body"));
        if (typeof dialog.showModal === "function") {
            dialog.showModal();
            return;
        }
        dialog.setAttribute("open", "open");
    }

    function formatModalBody(body) {
        if (!body || body.getAttribute("data-json-formatted") === "true") {
            return;
        }
        try {
            var parsed = JSON.parse(body.textContent || "");
            var formatted = JSON.stringify(parsed, null, 2);
            body.textContent = formatted;
        } catch (ignore) {
            // Keep non-JSON content exactly as it came from the database.
        }
        body.setAttribute("data-json-formatted", "true");
    }

    function closeDialog(closeButton) {
        var dialog = findParent(closeButton, "json-preview-dialog");
        if (!dialog) {
            return;
        }
        if (typeof dialog.close === "function") {
            dialog.close();
            return;
        }
        dialog.removeAttribute("open");
    }

    document.addEventListener("click", function (event) {
        var target = event.target;
        if (!target || !target.classList) {
            return;
        }
        if (target.classList.contains("json-preview-open")) {
            openDialog(target);
            return;
        }
        if (target.classList.contains("json-modal-close")) {
            closeDialog(target);
        }
    });
}());
