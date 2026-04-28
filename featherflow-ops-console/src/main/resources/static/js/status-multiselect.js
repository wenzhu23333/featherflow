(function () {
    function findHiddenInput(root) {
        var fieldGroup = root.closest(".field-group");
        return fieldGroup ? fieldGroup.querySelector('input[type="hidden"][name="status"]') : null;
    }

    function selectedValues(checkboxes) {
        return checkboxes
            .filter(function (checkbox) {
                return checkbox.checked;
            })
            .map(function (checkbox) {
                return checkbox.value;
            });
    }

    function updateSelection(root) {
        var hiddenInput = findHiddenInput(root);
        var summary = root.querySelector("[data-multi-select-summary]");
        var checkboxes = Array.prototype.slice.call(root.querySelectorAll('input[type="checkbox"]'));
        var values = selectedValues(checkboxes);
        if (hiddenInput) {
            hiddenInput.value = values.join(",");
        }
        if (summary) {
            summary.textContent = values.length === 0 ? "全部状态" : values.join(", ");
        }
    }

    function close(root) {
        var trigger = root.querySelector(".multi-select-trigger");
        root.classList.remove("is-open");
        if (trigger) {
            trigger.setAttribute("aria-expanded", "false");
        }
    }

    function toggle(root) {
        var trigger = root.querySelector(".multi-select-trigger");
        var isOpen = root.classList.toggle("is-open");
        if (trigger) {
            trigger.setAttribute("aria-expanded", isOpen ? "true" : "false");
        }
    }

    function init(root) {
        var trigger = root.querySelector(".multi-select-trigger");
        var checkboxes = Array.prototype.slice.call(root.querySelectorAll('input[type="checkbox"]'));
        updateSelection(root);

        if (trigger) {
            trigger.addEventListener("click", function () {
                toggle(root);
            });
        }

        checkboxes.forEach(function (checkbox) {
            checkbox.addEventListener("change", function () {
                updateSelection(root);
            });
        });
    }

    var roots = Array.prototype.slice.call(document.querySelectorAll("[data-multi-select]"));
    roots.forEach(init);

    document.addEventListener("click", function (event) {
        roots.forEach(function (root) {
            if (!root.contains(event.target)) {
                close(root);
            }
        });
    });

    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape") {
            return;
        }
        roots.forEach(close);
    });
})();
