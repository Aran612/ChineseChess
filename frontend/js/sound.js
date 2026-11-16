(function () {
    "use strict";

    let context = null;

    function audio() {
        if (typeof window.AudioContext !== "function" && typeof window.webkitAudioContext !== "function") {
            return null;
        }
        if (!context) {
            const Ctor = window.AudioContext || window.webkitAudioContext;
            context = new Ctor();
        }
        if (context.state === "suspended") {
            context.resume();
        }
        return context;
    }

    function tone(frequency, duration, options) {
        const ctx = audio();
        if (!ctx) {
            return;
        }
        const config = options || {};
        const start = ctx.currentTime + (config.delay || 0);
        const oscillator = ctx.createOscillator();
        const gain = ctx.createGain();
        oscillator.type = config.type || "sine";
        oscillator.frequency.setValueAtTime(frequency, start);
        if (config.slide) {
            oscillator.frequency.exponentialRampToValueAtTime(Math.max(40, config.slide), start + duration);
        }
        gain.gain.setValueAtTime(0.0001, start);
        gain.gain.exponentialRampToValueAtTime(config.gain || 0.08, start + 0.012);
        gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
        oscillator.connect(gain);
        gain.connect(ctx.destination);
        oscillator.start(start);
        oscillator.stop(start + duration + 0.03);
    }

    function chord(frequencies, spacing) {
        frequencies.forEach(function (frequency, index) {
            tone(frequency, 0.32, {
                type: "triangle",
                gain: 0.06,
                delay: index * (spacing || 0.09)
            });
        });
    }

    window.XiangqiSound = {
        unlock: function () {
            audio();
        },
        select: function () {
            tone(520, 0.05, { type: "sine", gain: 0.045 });
        },
        move: function () {
            tone(330, 0.07, { type: "triangle", gain: 0.06, slide: 260 });
        },
        capture: function () {
            tone(210, 0.12, { type: "sawtooth", gain: 0.055, slide: 130 });
            tone(150, 0.14, { type: "square", gain: 0.035, delay: 0.03, slide: 100 });
        },
        check: function () {
            tone(720, 0.09, { type: "triangle", gain: 0.06 });
            tone(920, 0.12, { type: "triangle", gain: 0.05, delay: 0.1 });
        },
        win: function () {
            chord([523.25, 659.25, 783.99, 1046.5], 0.08);
        },
        draw: function () {
            chord([392, 440, 523.25], 0.1);
        },
        error: function () {
            tone(140, 0.16, { type: "sawtooth", gain: 0.05 });
        }
    };
})();
