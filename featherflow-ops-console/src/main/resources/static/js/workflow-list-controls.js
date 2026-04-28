(function () {
    function updateHiddenInput(id, value) {
        var input = document.getElementById(id);
        if (input) {
            input.value = value;
        }
    }

    function submitFilterForm() {
        var form = document.getElementById("workflow-filter-form");
        if (!form) {
            return;
        }

        if (typeof form.requestSubmit === "function") {
            form.requestSubmit();
            return;
        }

        var event = new Event("submit", {
            bubbles: true,
            cancelable: true
        });
        if (form.dispatchEvent(event)) {
            form.submit();
        }
    }

    document.addEventListener("change", function (event) {
        var target = event.target;
        if (!target || typeof target.matches !== "function") {
            return;
        }

        if (target.matches("[data-workflow-page-size-control]")) {
            updateHiddenInput("workflow-size-input", target.value);
            submitFilterForm();
            return;
        }

        if (target.matches("[data-workflow-order-control]")) {
            updateHiddenInput("workflow-order-input", target.value);
            submitFilterForm();
        }
    });
})();
