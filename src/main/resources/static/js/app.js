/*
 * Copyright (c) 2026 Kismet Software LLC. All rights reserved.
 *
 * The only script on politigif (strict CSP: script-src 'self'). Three jobs:
 * copy a share link, open the system share sheet, and send an upload as a raw image/gif body.
 */
(function () {
    'use strict';

    function csrfHeaders() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        var headers = {'Content-Type': 'image/gif'};
        if (token && header) {
            headers[header.getAttribute('content')] = token.getAttribute('content');
        }
        return headers;
    }

    // ---- Copy link ----
    document.querySelectorAll('[data-copy]').forEach(function (button) {
        button.addEventListener('click', function () {
            var url = button.getAttribute('data-copy');
            var done = function () {
                var note = document.querySelector('[data-copied]');
                if (note) {
                    note.hidden = false;
                }
            };
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(url).then(done, function () {
                    window.prompt('Copy this link:', url);
                });
            } else {
                window.prompt('Copy this link:', url);
            }
        });
    });

    // ---- Share (only where the browser has a share sheet) ----
    document.querySelectorAll('[data-share]').forEach(function (button) {
        if (!navigator.share) {
            return;
        }
        button.hidden = false;
        button.addEventListener('click', function () {
            navigator.share({
                title: button.getAttribute('data-title'),
                url: button.getAttribute('data-share')
            }).catch(function () { /* dismissed */ });
        });
    });

    // ---- Upload ----
    var form = document.getElementById('upload-form');
    if (form) {
        var errorBox = form.querySelector('[data-upload-error]');
        var statusBox = form.querySelector('[data-upload-status]');
        var maxBytes = parseInt(form.getAttribute('data-max-bytes'), 10);
        var showError = function (message) {
            statusBox.hidden = true;
            errorBox.textContent = message;
            errorBox.hidden = false;
        };
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            errorBox.hidden = true;
            var file = form.querySelector('#file').files[0];
            if (!file) {
                showError('Choose a GIF first.');
                return;
            }
            if (file.size > maxBytes) {
                showError('That GIF is over 20 MB. Try a smaller one.');
                return;
            }
            var params = new URLSearchParams({
                title: form.querySelector('#title').value,
                tags: form.querySelector('#tags').value
            });
            var button = form.querySelector('button[type="submit"]');
            button.disabled = true;
            statusBox.hidden = false;
            fetch('/upload?' + params.toString(), {
                method: 'POST',
                headers: csrfHeaders(),
                body: file,
                credentials: 'same-origin'
            }).then(function (response) {
                return response.json().catch(function () {
                    return {error: 'Upload failed. Try again.'};
                });
            }).then(function (result) {
                if (result.url) {
                    window.location.href = result.url;
                } else {
                    button.disabled = false;
                    showError(result.error || 'Upload failed. Try again.');
                }
            }).catch(function () {
                button.disabled = false;
                showError('Upload failed. Check your connection and try again.');
            });
        });
    }
})();
