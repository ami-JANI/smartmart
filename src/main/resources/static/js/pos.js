/* POS helpers (SCRUM-44, SCRUM-46, SCRUM-47). Plain JavaScript, no libraries.
   A keyboard-wedge barcode scanner types the barcode and presses Enter, so the
   barcode field must always have focus. The payment page shows the change due
   while the cashier types. The server checks everything again, and the pages
   work without this file. */
(function () {
    'use strict';

    function formatMoney(cents) {
        return '\u09F3 ' + (cents / 100).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    // Payment page: change due = cash received - total, in whole paisa to avoid rounding drift.
    var cash = document.getElementById('cashReceived');
    var changeDue = document.getElementById('change-due');
    var cashShort = document.getElementById('cash-short');
    if (cash && changeDue && cash.form && cash.form.dataset.total) {
        var totalCents = Math.round(parseFloat(cash.form.dataset.total) * 100);
        var update = function () {
            var received = Math.round(parseFloat(cash.value) * 100);
            if (isNaN(received) || isNaN(totalCents)) {
                changeDue.textContent = formatMoney(0);
                if (cashShort) { cashShort.hidden = true; }
                return;
            }
            var short = received < totalCents;
            changeDue.textContent = formatMoney(Math.max(received - totalCents, 0));
            changeDue.classList.toggle('is-short', short);
            if (cashShort) { cashShort.hidden = !short; }
        };
        cash.addEventListener('input', update);
        update();
    }

    // Invoice page: the Print button is hidden until this script runs, because it needs JavaScript.
    Array.prototype.forEach.call(document.querySelectorAll('[data-print]'), function (button) {
        button.hidden = false;
        button.addEventListener('click', function () { window.print(); });
    });

    var field = document.querySelector('[data-autofocus]');
    if (!field) { return; }

    field.focus();
    if (field.select) { field.select(); }

    // Clicking empty space gives the focus back to the scan field.
    document.addEventListener('click', function (event) {
        if (!event.target.closest('a, button, input, select, textarea, summary, label')) {
            field.focus();
        }
    });

    // So does coming back to the window or tab.
    window.addEventListener('focus', function () {
        var active = document.activeElement;
        if (!active || active === document.body) { field.focus(); }
    });
}());
