// This must run before the application bundle so even entry-point failures are visible.
(function () {
    "use strict";

    const panel = document.getElementById("showcase-startup");
    if (!panel) return;

    const title = document.getElementById("showcase-startup-title");
    const progress = document.getElementById("showcase-startup-progress");
    const progressValue = document.getElementById("showcase-startup-progress-value");
    const status = document.getElementById("showcase-startup-status");
    const hint = document.getElementById("showcase-startup-hint");
    const details = document.getElementById("showcase-startup-error");
    const retry = document.getElementById("showcase-startup-retry");
    if (!title || !progress || !progressValue || !status || !hint || !details || !retry) return;

    const chinese = /^zh\b/i.test(navigator.language || "");
    const text = chinese ? {
        title: "动态图片画廊，展示精彩瞬间",
        loading: "加载中...",
        loadingHint: "正在准备 Showcase 资源...",
        failed: "Showcase 启动失败",
        failureHint: "请检查网络后重试。如果仍然失败，请保留下方错误信息。",
        timeoutHint: "应用仍在加载。你可以继续等待，或检查网络后重新加载。",
        script: "无法加载应用脚本：",
        unknown: "发生了未知启动错误。",
        retry: "重新加载"
    } : {
        title: "Showcase a gallery of lively moments.",
        loading: "Loading...",
        loadingHint: "Preparing the resources Showcase needs to start...",
        failed: "Showcase could not start",
        failureHint: "Check your connection and retry. If it still fails, keep the error below for troubleshooting.",
        timeoutHint: "The app is still loading. You can keep waiting, or check your connection and reload.",
        script: "Could not load the application script: ",
        unknown: "An unknown startup error occurred.",
        retry: "Reload"
    };
    const progressByStage = Object.freeze({
        application: 18,
        engine: 42,
        font: 68,
        render: 90
    });
    const reduceMotion = typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let completed = false;
    let failed = false;
    let readinessScheduled = false;
    let currentProgress = 0;
    const timeout = setTimeout(function () {
        if (completed || failed) return;
        // A slow connection is not a failed boot: a later rendered frame can still finish.
        hint.textContent = text.timeoutHint;
        retry.hidden = false;
    }, 120000);

    panel.lang = chinese ? "zh-CN" : "en";
    title.textContent = text.title;
    progress.setAttribute("aria-label", text.loading);
    status.textContent = text.loading;
    hint.textContent = text.loadingHint;
    retry.textContent = text.retry;
    retry.addEventListener("click", function () { window.location.reload(); });

    // Compose still reports startup milestones through this API. The splash maps
    // those milestones to one quiet progress indicator without changing the copy.
    function setStage(stage) {
        if (completed || failed) return;
        const nextProgress = progressByStage[stage];
        if (nextProgress === undefined || nextProgress <= currentProgress) return;
        currentProgress = nextProgress;
        progressValue.style.width = nextProgress + "%";
        progress.setAttribute("aria-valuenow", String(nextProgress));
    }

    setStage("application");

    function fail(reason) {
        if (completed || failed) return;
        failed = true;
        clearTimeout(timeout);
        panel.dataset.state = "failed";
        panel.setAttribute("aria-busy", "false");
        status.setAttribute("role", "alert");
        status.setAttribute("aria-live", "assertive");
        title.textContent = text.failed;
        status.textContent = text.failureHint;
        hint.hidden = true;
        // Error messages may contain server-controlled content. Never interpret it as HTML.
        details.textContent = String(reason && reason.message ? reason.message : reason || text.unknown).slice(0, 2000);
        details.hidden = false;
        retry.hidden = false;
    }

    function onError(event) {
        if (event.target && event.target.tagName === "SCRIPT") {
            // Hosting providers may inject optional analytics scripts. Their blocked
            // downloads must not hide an otherwise working application.
            if (event.target.id === "showcase-app-script") {
                fail(text.script + (event.target.getAttribute("src") || "ShowcaseApp.js"));
            }
        } else if (event.message || event.error) {
            fail(event.error || event.message);
        }
    }

    function onRejection(event) {
        fail(event.reason);
    }

    window.addEventListener("error", onError, true);
    window.addEventListener("unhandledrejection", onRejection);
    window.ShowcaseStartup = Object.freeze({
        setStage: setStage,
        fail: fail,
        ready: function () {
            if (completed || failed || readinessScheduled) return;
            readinessScheduled = true;
            // Called after Compose draws its content. Let that frame reach the screen
            // before removing the HTML overlay; failures during that frame remain visible.
            requestAnimationFrame(function () {
                requestAnimationFrame(function () {
                    if (completed || failed) return;
                    completed = true;
                    clearTimeout(timeout);
                    window.removeEventListener("error", onError, true);
                    window.removeEventListener("unhandledrejection", onRejection);
                    panel.setAttribute("aria-busy", "false");
                    panel.setAttribute("aria-hidden", "true");
                    panel.classList.add("is-ready");

                    let removed = false;
                    function removePanel() {
                        if (removed) return;
                        removed = true;
                        panel.remove();
                    }
                    panel.addEventListener("transitionend", function (event) {
                        if (event.target === panel && event.propertyName === "opacity") removePanel();
                    });
                    setTimeout(removePanel, reduceMotion ? 220 : 360);
                });
            });
        }
    });
})();
