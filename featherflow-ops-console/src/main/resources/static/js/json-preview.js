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
        if (typeof dialog.showModal === "function") {
            dialog.showModal();
            return;
        }
        dialog.setAttribute("open", "open");
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
