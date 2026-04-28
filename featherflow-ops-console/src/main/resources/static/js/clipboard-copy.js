(function () {
    function findCopyButton(target) {
        var current = target;
        while (current && current !== document) {
            if (current.classList && current.classList.contains("copy-value-button")) {
                return current;
            }
            current = current.parentNode;
        }
        return null;
    }

    function markCopied(button) {
        var oldTitle = button.getAttribute("title") || "复制";
        button.classList.add("is-copied");
        button.setAttribute("title", "已复制");
        window.setTimeout(function () {
            button.classList.remove("is-copied");
            button.setAttribute("title", oldTitle);
        }, 1400);
    }

    function fallbackCopy(value, button) {
        var textarea = document.createElement("textarea");
        textarea.value = value;
        textarea.setAttribute("readonly", "readonly");
        textarea.style.position = "fixed";
        textarea.style.top = "-9999px";
        document.body.appendChild(textarea);
        textarea.select();
        try {
            document.execCommand("copy");
            markCopied(button);
        } finally {
            document.body.removeChild(textarea);
        }
    }

    function copyValue(button) {
        var value = button.getAttribute("data-copy-value") || "";
        if (!value) {
            return;
        }
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(value).then(function () {
                markCopied(button);
            }, function () {
                fallbackCopy(value, button);
            });
            return;
        }
        fallbackCopy(value, button);
    }

    document.addEventListener("click", function (event) {
        var button = findCopyButton(event.target);
        if (!button) {
            return;
        }
        event.preventDefault();
        copyValue(button);
    });
}());
