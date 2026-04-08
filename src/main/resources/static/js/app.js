/**
 * KANGMUSIC — Global Player Logic
 * Queue lưu localStorage (không mất khi refresh)
 * Auto-fill queue cùng thể loại khi hết bài
 */

/**
 * CRIT-2 FIX: Reads the XSRF-TOKEN cookie set by Spring Security's
 * CookieCsrfTokenRepository so fetch() POST calls can include it.
 */
function getCsrfToken() {
    const match = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));
    return match ? decodeURIComponent(match.split('=')[1]) : '';
}





document.addEventListener('DOMContentLoaded', () => {

    // ── Active nav ─────────────────────────────────────────────────────────
    function updateActiveNav() {
        const path = window.location.pathname;
        document.querySelectorAll('.sidebar-nav a').forEach(a => {
            a.classList.toggle('active', a.getAttribute('href') === path);
        });
    }

    function isShellRoute(urlObj) {
        const path = urlObj.pathname;
        return path === '/'
            || path === '/library'
            || path === '/profile'
            || path === '/admin'
            || path.startsWith('/track/')
            || path.startsWith('/playlists');
    }

    function navigateMainContent(url) {
        if (!window.htmx) return false;
        const target = document.getElementById('main-content');
        if (!target) return false;

        let urlObj;
        try {
            urlObj = new URL(url, window.location.origin);
        } catch {
            return false;
        }
        if (urlObj.origin !== window.location.origin) return false;
        if (!isShellRoute(urlObj)) return false;

        window.htmx.ajax('GET', `${urlObj.pathname}${urlObj.search}`, {
            target: '#main-content',
            select: '#main-content',
            swap: 'outerHTML',
            pushUrl: true
        });
        return true;
    }

    function shouldHandleSpaLink(link, e) {
        if (!link || !window.htmx) return false;
        if (e.defaultPrevented) return false;
        if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return false;
        if (link.hasAttribute('download')) return false;
        const target = link.getAttribute('target');
        if (target && target !== '_self') return false;
        const href = link.getAttribute('href');
        if (!href || href.startsWith('#') || href.startsWith('javascript:')) return false;
        if (link.hasAttribute('hx-get') || link.hasAttribute('hx-post') || link.hasAttribute('hx-put') || link.hasAttribute('hx-delete')) return false;
        return true;
    }

    document.body.addEventListener('click', e => {
        const link = e.target.closest('a[href]');
        if (!shouldHandleSpaLink(link, e)) return;

        if (navigateMainContent(link.href)) {
            e.preventDefault();
        }
    });

    document.body.addEventListener('htmx:afterOnLoad', updateActiveNav);
    document.body.addEventListener('htmx:afterSwap', e => {
        const target = e.detail?.target || e.target;
        if (target && target.id === 'main-content') {
            target.scrollTop = 0;
            initContentAreaScroll();
            updateActiveNav();
        }
    });
    updateActiveNav();
    initGlobalSearchDropdown();

    // Global search dropdown (recent + suggestions)
    function initGlobalSearchDropdown() {
        const input     = document.getElementById('global-search-input');
        const form      = document.getElementById('global-search-form');
        const dropdown  = document.getElementById('global-search-dropdown');
        const content   = document.getElementById('search-dropdown-content');
        const clearBtn  = document.getElementById('global-search-clear');
        if (!input || !form || !dropdown || !content) return;

        const RECENT_KEY = 'km_recent_searches';
        const MAX_RECENT = 8;
        let debounceTimer = null;
        let lastSuggestions = [];

        function loadRecent() {
            try { return JSON.parse(localStorage.getItem(RECENT_KEY) || '[]'); }
            catch { return []; }
        }
        function saveRecent(list) {
            try { localStorage.setItem(RECENT_KEY, JSON.stringify(list.slice(0, MAX_RECENT))); }
            catch {}
        }
        function pushRecent(q) {
            if (!q || !q.trim()) return;
            const list = loadRecent().filter(item => item.q.toLowerCase() !== q.toLowerCase());
            list.unshift({ q, ts: Date.now() });
            saveRecent(list);
        }

        function renderRecent() {
            const list = loadRecent();
            if (!list.length) return '';
            const items = list.map(item => `
                <button class=\"search-row\" data-q=\"${item.q}\">
                    <span class=\"search-row-icon\">⌘</span>
                    <div class=\"search-row-text\">
                        <div class=\"search-row-title\">${item.q}</div>
                        <div class=\"search-row-sub\">Tìm kiếm gần đây</div>
                    </div>
                </button>
            `).join('');
            return `
                <div class=\"search-section\">
                    <div class=\"search-section-title\">Các tìm kiếm gần đây</div>
                    <div class=\"search-list\">${items}</div>
                    <button class=\"search-clear-btn\" id=\"btn-clear-recents\">Xoá các tìm kiếm gần đây</button>
                </div>
            `;
        }

        function renderSuggestions(items) {
            if (!items || !items.length) return '';
            const rows = items.map(item => `
                <button class=\"search-row\" data-q=\"${item.title}\" data-id=\"${item.id || ''}\">
                    <span class=\"search-row-icon\">
                        ${item.posterSrc ? `<img src=\"${item.posterSrc}\" class=\"search-row-thumb\">`
                          : item.posterFilename ? `<img src=\"/stream/${item.posterFilename}\" class=\"search-row-thumb\">`
                          : '🔍'}
                    </span>
                    <div class=\"search-row-text\">
                        <div class=\"search-row-title\">${item.title}</div>
                        <div class=\"search-row-sub\">${item.artist || ''}</div>
                    </div>
                </button>
            `).join('');
            return `
                <div class=\"search-section\">
                    <div class=\"search-section-title\">Gợi ý</div>
                    <div class=\"search-list\">${rows}</div>
                </div>
            `;
        }

        function openDropdown(html) {
            content.innerHTML = html;
            dropdown.classList.remove('hidden');
        }
        function closeDropdown() { dropdown.classList.add('hidden'); }

        function buildDropdown(q, suggestions=[]) {
            const recentHtml = renderRecent();
            const suggestHtml = renderSuggestions(suggestions);
            const html = (recentHtml || suggestHtml) ? `${recentHtml}${suggestHtml}` :
                `<div class=\"search-section\"><div class=\"search-section-title\">Không có gợi ý</div></div>`;
            openDropdown(html);
        }

        function fetchSuggest(q) {
            if (!q || !q.trim()) { buildDropdown('', []); return; }
            fetch(`/api/search?q=${encodeURIComponent(q)}`)
                .then(r => r.ok ? r.json() : [])
                .then(data => { lastSuggestions = data || []; buildDropdown(q, data); })
                .catch(() => { lastSuggestions = []; buildDropdown(q, []); });
        }

        input.addEventListener('focus', () => { buildDropdown('', []); });
        input.addEventListener('input', () => {
            const q = input.value;
            clearTimeout(debounceTimer);
            debounceTimer = setTimeout(() => fetchSuggest(q), 180);
        });
        input.addEventListener('keydown', e => {
            if (e.key === 'Enter') {
                // Chỉ submit form bình thường — không tự fill tên bài vào input
                pushRecent(input.value);
                // allow normal form submit (HTMX) by not preventing default
            }
        });
        form.addEventListener('submit', e => {
            pushRecent(input.value);

            const formData = new FormData(form);
            const params = new URLSearchParams(formData);
            const action = form.getAttribute('action') || window.location.pathname;
            const nextUrl = params.toString() ? `${action}?${params.toString()}` : action;

            if (navigateMainContent(nextUrl)) {
                e.preventDefault();
                closeDropdown();
            }
        });
        clearBtn?.addEventListener('click', () => { input.value=''; input.focus(); buildDropdown('', []); });

        // Dùng mousedown thay vì click để xử lý trước khi blur đóng dropdown
        content.addEventListener('mousedown', e => {
            // Xử lý nút xoá lịch sử
            if (e.target.id === 'btn-clear-recents') {
                e.preventDefault();
                saveRecent([]);
                buildDropdown('', []);
                return;
            }

            const row = e.target.closest('.search-row');
            if (!row) return;
            e.preventDefault(); // Ngăn input mất focus trước khi xử lý

            const q = row.dataset.q || '';
            const id = row.dataset.id || '';
            input.value = q;
            pushRecent(q);
            closeDropdown();

            if (id) {
                // Gợi ý track — vào thẳng trang track
                const trackUrl = `/track/${id}`;
                if (!navigateMainContent(trackUrl)) {
                    window.location.href = trackUrl;
                }
            } else {
                // Tìm kiếm gần đây — submit form
                form.requestSubmit();
            }
        });
        document.addEventListener('click', e => {
            if (!dropdown.contains(e.target) && !form.contains(e.target)) closeDropdown();
        });
    }

    // ── Player elements ────────────────────────────────────────────────────
    const audio         = document.getElementById('master-audio');
    const container     = document.getElementById('master-player-container');
    const titleEl       = document.getElementById('player-title');
    const artistEl      = document.getElementById('player-artist');
    const artworkIcon   = document.getElementById('player-artwork-icon');
    const btnPlay       = document.getElementById('btn-play-pause');
    const btnPrev       = document.getElementById('btn-prev');
    const btnNext       = document.getElementById('btn-next');
    const btnShuffle    = document.getElementById('btn-shuffle');
    const btnRepeat     = document.getElementById('btn-repeat');
    const btnLike       = document.getElementById('btn-like');
    const btnQueue      = document.getElementById('btn-queue');
    const btnLyrics     = document.getElementById('btn-lyrics');
    const seekBar       = document.getElementById('player-seek-bar');
    const volumeSlider  = document.getElementById('volume-slider');
    const currentTimeEl = document.getElementById('player-current-time');
    const totalTimeEl   = document.getElementById('player-total-time');
    const btnPlayerMini   = document.getElementById('btn-player-mini');
    const btnPlayerExpand = document.getElementById('btn-player-expand');

    // Right Sidebar Elements
    const rightSidebar      = document.getElementById('right-sidebar');
    const queueListSide     = document.getElementById('queue-list-sidebar');
    const rsBtnFullscreen   = document.getElementById('rs-btn-fullscreen');

    // Expanded Player Elements
    const expandedOverlay       = document.getElementById('player-expanded-overlay');
    const expandedScroll        = document.getElementById('player-expanded-scroll');
    const expandedCoverWrap     = document.getElementById('expanded-cover-wrap');
    const expandedContext       = document.getElementById('expanded-context');
    const expandedVideo         = document.getElementById('expanded-video');
    const expandedCoverImg      = document.getElementById('expanded-cover-img');
    const expandedCoverIcon     = document.getElementById('expanded-cover-icon');
    const expandedTrackTitle    = document.getElementById('expanded-track-title');
    const expandedTrackArtist   = document.getElementById('expanded-track-artist');
    const expandedLyricsPreview = document.getElementById('expanded-lyrics-preview');
    const expandedQueuePreview  = document.getElementById('expanded-queue-preview');
    const btnExpandedMini       = document.getElementById('btn-expanded-mini');
    const btnExpandedClose      = document.getElementById('btn-expanded-close');

    // Mini Player Elements
    const miniWindow      = document.getElementById('player-mini-window');
    const btnMiniClose    = document.getElementById('btn-mini-close');
    const btnMiniExpand   = document.getElementById('btn-mini-expand');
    const btnMiniPlay     = document.getElementById('btn-mini-play-pause');
    const miniVideo       = document.getElementById('mini-video');
    const miniCoverImg    = document.getElementById('mini-cover-img');
    const miniCoverIcon   = document.getElementById('mini-cover-icon');
    const miniTrackTitle  = document.getElementById('mini-track-title');
    const miniTrackArtist = document.getElementById('mini-track-artist');
    const miniSeekBar     = document.getElementById('mini-seek-bar');
    const miniCurrentTime = document.getElementById('mini-current-time');
    const miniTotalTime   = document.getElementById('mini-total-time');
    let lyricsOverlay     = document.getElementById('lyrics-overlay');
    let lyricsOverlayTitle = document.getElementById('lyrics-overlay-track-name');
    let lyricsOverlayContent = document.getElementById('lyrics-overlay-content');
    let btnCloseLyricsOverlay = document.getElementById('btn-close-lyrics-overlay');

    // Fallback: if overlay markup is not rendered from fragments, create it dynamically.
    if (!lyricsOverlay) {
        const overlay = document.createElement('div');
        overlay.className = 'lyrics-overlay';
        overlay.id = 'lyrics-overlay';
        overlay.innerHTML = `
            <button class="lyrics-overlay-close" id="btn-close-lyrics-overlay" title="Dong loi bai hat">✕</button>
            <div class="lyrics-overlay-inner">
                <div class="lyrics-overlay-title" id="lyrics-overlay-track-name">Loi bai hat</div>
                <div id="lyrics-overlay-content" class="lyrics-overlay-text"></div>
            </div>
        `;
        document.body.appendChild(overlay);
        lyricsOverlay = overlay;
        lyricsOverlayTitle = overlay.querySelector('#lyrics-overlay-track-name');
        lyricsOverlayContent = overlay.querySelector('#lyrics-overlay-content');
        btnCloseLyricsOverlay = overlay.querySelector('#btn-close-lyrics-overlay');
    }

    if (!audio) return; // trang không có player

    // ── State ──────────────────────────────────────────────────────────────
    let queue       = loadQueue();
    let queueIndex  = loadQueueIndex();
    let shuffle     = false;
    let repeatMode  = 'none';        // 'none' | 'one' | 'all'
    let isDragging  = false;
    let currentTrack = null;
    let isExpandedPlayerVisible = false;
    let isMiniPlayerVisible = false;
    let miniPopup = null;
    let miniPopupDom = null;

    const PLAY_SVG  = `<svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28"><path d="M8 5v14l11-7z"/></svg>`;
    const PAUSE_SVG = `<svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>`;
    const MINI_PLAY_ICON = '>';
    const MINI_PAUSE_ICON = '||';
    const VIDEO_ICON = '\uD83C\uDFAC';
    const AUDIO_ICON = '\uD83C\uDFB5';

    function hasPlayableTrack() {
        return !!(currentTrack && currentTrack.src);
    }

    function getPlayingContextLabel() {
        const idx = queueIndex + 1;
        const total = queue.length;
        if (total > 0) return `Dang phat ${idx}/${total}`;
        return 'Dang phat';
    }

    function renderExpandedQueuePreview() {
        if (!expandedQueuePreview) return;
        const upcoming = queue.slice(queueIndex + 1, queueIndex + 6);
        if (upcoming.length === 0) {
            expandedQueuePreview.innerHTML = '<div class="player-expanded-empty">Danh sach cho trong.</div>';
            return;
        }
        expandedQueuePreview.innerHTML = upcoming.map((track, i) => {
            const idx = queueIndex + 1 + i;
            const emoji = track.type === 'VIDEO' ? VIDEO_ICON : AUDIO_ICON;
            return `
                <button type="button" class="player-expanded-queue-item" data-idx="${idx}">
                    <span>${emoji}</span>
                    <span>
                        <div class="player-expanded-queue-item-title">${escHtml(track.title || 'Unknown title')}</div>
                        <div class="player-expanded-queue-item-artist">${escHtml(track.artist || 'Unknown artist')}</div>
                    </span>
                </button>
            `;
        }).join('');
    }

    function clearExpandedVideo() {
        if (!expandedVideo) return;
        expandedVideo.pause();
        expandedVideo.dataset.src = '';
        expandedVideo.removeAttribute('src');
        expandedVideo.load();
        expandedVideo.style.display = 'none';
        expandedCoverWrap?.classList.remove('video-mode');
    }

    function syncExpandedScrollState() {
        if (!expandedOverlay || !expandedScroll) return;
        expandedOverlay.classList.toggle('scrolled', expandedScroll.scrollTop > 24);
    }

    function syncExpandedVideoWithAudio(forceSeek = false) {
        if (!expandedVideo || !currentTrack || currentTrack.type !== 'VIDEO') return;
        if (!isExpandedPlayerVisible) {
            expandedVideo.pause();
            return;
        }
        const audioTime = isFinite(audio.currentTime) ? audio.currentTime : 0;
        if (forceSeek || Math.abs((expandedVideo.currentTime || 0) - audioTime) > 0.35) {
            try { expandedVideo.currentTime = audioTime; } catch (e) { /* ignore */ }
        }
        if (audio.paused) {
            expandedVideo.pause();
        } else {
            expandedVideo.play().catch(() => {});
        }
    }

    function setExpandedBackdropImage(poster) {
        if (!expandedOverlay) return;
        if (poster && poster !== 'null') {
            expandedOverlay.style.setProperty('--expanded-backdrop-image', `url('/stream/${poster}')`);
            expandedOverlay.classList.add('has-backdrop');
        } else {
            expandedOverlay.style.removeProperty('--expanded-backdrop-image');
            expandedOverlay.classList.remove('has-backdrop');
        }
    }

    function updateExpandedPlayer(track) {
        if (!expandedOverlay) return;
        expandedContext && (expandedContext.textContent = getPlayingContextLabel());
        if (!track) {
            setExpandedBackdropImage('');
            if (expandedTrackTitle) expandedTrackTitle.textContent = 'Chua co bai hat';
            if (expandedTrackArtist) expandedTrackArtist.textContent = '-';
            if (expandedLyricsPreview) expandedLyricsPreview.innerHTML = '<div class="player-expanded-empty">Hay phat mot bai hat de xem.</div>';
            if (expandedCoverImg) expandedCoverImg.style.display = 'none';
            if (expandedCoverIcon) expandedCoverIcon.style.display = 'flex';
            clearExpandedVideo();
            renderExpandedQueuePreview();
            return;
        }

        if (expandedTrackTitle) expandedTrackTitle.textContent = track.title || 'Unknown title';
        if (expandedTrackArtist) expandedTrackArtist.textContent = track.artist || 'Unknown artist';
        if (expandedLyricsPreview) {
            if (track.lyrics && track.lyrics.trim()) {
                const preview = track.lyrics.split(/\r?\n/).slice(0, 10).join('\n');
                expandedLyricsPreview.textContent = preview;
            } else {
                expandedLyricsPreview.innerHTML = '<div class="player-expanded-empty">Khong co loi bai hat.</div>';
            }
        }

        setExpandedBackdropImage(track.poster || '');

        if (track.type === 'VIDEO' && expandedVideo) {
            if (expandedVideo.dataset.src !== track.src) {
                expandedVideo.src = track.src;
                expandedVideo.dataset.src = track.src || '';
                expandedVideo.load();
            }
            expandedCoverWrap?.classList.add('video-mode');
            expandedVideo.style.display = 'block';
            if (expandedCoverImg) expandedCoverImg.style.display = 'none';
            if (expandedCoverIcon) expandedCoverIcon.style.display = 'none';
            syncExpandedVideoWithAudio(true);
        } else {
            clearExpandedVideo();
            if (expandedCoverImg && expandedCoverIcon) {
                if (track.poster && track.poster !== 'null') {
                    expandedCoverImg.src = `/stream/${track.poster}`;
                    expandedCoverImg.style.display = 'block';
                    expandedCoverIcon.style.display = 'none';
                } else {
                    expandedCoverImg.style.display = 'none';
                    expandedCoverIcon.style.display = 'flex';
                    expandedCoverIcon.textContent = track.type === 'VIDEO' ? VIDEO_ICON : AUDIO_ICON;
                }
            }
        }

        renderExpandedQueuePreview();
    }

    function openExpandedPlayer() {
        if (!hasPlayableTrack()) {
            showToast('Hay phat mot bai hat truoc!');
            return;
        }
        if (!expandedOverlay) return;
        closeLyricsOverlay();
        closeMiniPlayer();
        isExpandedPlayerVisible = true;
        expandedOverlay.classList.add('visible');
        expandedOverlay.setAttribute('aria-hidden', 'false');
        if (expandedScroll) expandedScroll.scrollTop = 0;
        syncExpandedScrollState();
        btnPlayerExpand?.classList.add('btn-active');
        rsBtnFullscreen?.classList.add('btn-active');
        updateExpandedPlayer(currentTrack);
    }

    function closeExpandedPlayer() {
        if (!expandedOverlay) return;
        isExpandedPlayerVisible = false;
        expandedOverlay.classList.remove('visible');
        expandedOverlay.setAttribute('aria-hidden', 'true');
        syncExpandedScrollState();
        expandedVideo?.pause();
        btnPlayerExpand?.classList.remove('btn-active');
        rsBtnFullscreen?.classList.remove('btn-active');
    }

    function toggleExpandedPlayer() {
        if (isExpandedPlayerVisible) closeExpandedPlayer();
        else openExpandedPlayer();
    }

    function getMiniPopupMarkup() {
        return `
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>KangMusic Mini Player</title>
    <style>
        :root { color-scheme: dark; }
        * { box-sizing: border-box; }
        body {
            margin: 0;
            width: 100vw;
            height: 100vh;
            overflow: hidden;
            font-family: "Segoe UI", sans-serif;
            background: linear-gradient(180deg, #181818 0%, #101010 100%);
            color: #fff;
            display: flex;
            flex-direction: column;
        }
        .mini-head {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 10px 12px;
            border-bottom: 1px solid rgba(255,255,255,0.12);
            background: rgba(0,0,0,0.22);
        }
        .mini-label { font-size: 13px; font-weight: 700; letter-spacing: 0.03em; }
        .mini-actions { display: flex; gap: 6px; }
        .mini-icon-btn {
            width: 30px;
            height: 30px;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            color: #d8d8d8;
            background: transparent;
            font-size: 15px;
        }
        .mini-icon-btn:hover { background: rgba(255,255,255,0.12); color: #fff; }
        .mini-cover-wrap {
            position: relative;
            margin: 12px;
            border-radius: 12px;
            overflow: hidden;
            flex: 1;
            min-height: 210px;
            background: linear-gradient(135deg, #2a2a2a 0%, #1f1f1f 100%);
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .mini-cover-wrap img {
            width: 100%;
            height: 100%;
            object-fit: cover;
        }
        .mini-video {
            width: 100%;
            height: 100%;
            object-fit: contain;
            background: #000;
        }
        .mini-cover-wrap.video-mode {
            background: #000;
        }
        .mini-cover-icon {
            font-size: 56px;
            color: rgba(255,255,255,0.92);
        }
        .mini-play-btn {
            position: absolute;
            left: 50%;
            top: 50%;
            transform: translate(-50%, -50%);
            width: 62px;
            height: 62px;
            border-radius: 999px;
            border: none;
            cursor: pointer;
            background: #fff;
            color: #000;
            font-size: 24px;
            font-weight: 700;
        }
        .mini-meta { padding: 0 12px 6px; }
        .mini-title {
            font-size: 28px;
            font-weight: 800;
            line-height: 1.2;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .mini-artist {
            margin-top: 4px;
            color: #b3b3b3;
            font-size: 14px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .mini-progress {
            display: grid;
            grid-template-columns: 34px 1fr 34px;
            align-items: center;
            gap: 8px;
            padding: 8px 12px 12px;
        }
        .mini-time { font-size: 11px; color: #b3b3b3; text-align: center; }
        .mini-seek {
            width: 100%;
            -webkit-appearance: none;
            appearance: none;
            height: 4px;
            border-radius: 999px;
            background: #5a5a5a;
            outline: none;
        }
        .mini-seek::-webkit-slider-thumb {
            -webkit-appearance: none;
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #fff;
        }
        .mini-seek::-moz-range-thumb {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #fff;
            border: none;
        }
    </style>
</head>
<body>
    <div class="mini-head">
        <span class="mini-label">Mini player</span>
        <div class="mini-actions">
            <button type="button" class="mini-icon-btn" id="mini-popup-expand" title="Expand">&#x2932;</button>
            <button type="button" class="mini-icon-btn" id="mini-popup-close" title="Close">&#x2715;</button>
        </div>
    </div>
    <div class="mini-cover-wrap">
        <video id="mini-popup-video" class="mini-video" playsinline muted preload="metadata" style="display:none;"></video>
        <img id="mini-popup-cover-img" alt="Cover" style="display:none;">
        <div id="mini-popup-cover-icon" class="mini-cover-icon">${AUDIO_ICON}</div>
        <button type="button" class="mini-play-btn" id="mini-popup-play">${MINI_PLAY_ICON}</button>
    </div>
    <div class="mini-meta">
        <div class="mini-title" id="mini-popup-title">No track</div>
        <div class="mini-artist" id="mini-popup-artist">-</div>
    </div>
    <div class="mini-progress">
        <span class="mini-time" id="mini-popup-current">0:00</span>
        <input type="range" id="mini-popup-seek" class="mini-seek" min="0" max="100" step="0.1" value="0">
        <span class="mini-time" id="mini-popup-total">0:00</span>
    </div>
</body>
</html>`;
    }

    function hideInlineMiniPlayer() {
        if (!miniWindow) return;
        miniWindow.classList.remove('visible');
        miniWindow.setAttribute('aria-hidden', 'true');
    }

    function showInlineMiniPlayer() {
        if (!miniWindow) return;
        miniWindow.classList.add('visible');
        miniWindow.setAttribute('aria-hidden', 'false');
    }

    function clearMiniInlineVideo() {
        if (!miniVideo) return;
        miniVideo.pause();
        miniVideo.dataset.src = '';
        miniVideo.removeAttribute('src');
        miniVideo.load();
        miniVideo.style.display = 'none';
        miniVideo.closest('.player-mini-cover-wrap')?.classList.remove('video-mode');
    }

    function syncMiniInlineVideoWithAudio(forceSeek = false) {
        if (!miniVideo || !currentTrack || currentTrack.type !== 'VIDEO') return;
        if (!isMiniPlayerVisible || !miniWindow?.classList.contains('visible')) {
            miniVideo.pause();
            return;
        }
        const audioTime = isFinite(audio.currentTime) ? audio.currentTime : 0;
        if (forceSeek || Math.abs((miniVideo.currentTime || 0) - audioTime) > 0.35) {
            try { miniVideo.currentTime = audioTime; } catch (e) { /* ignore */ }
        }
        if (audio.paused) {
            miniVideo.pause();
        } else {
            miniVideo.play().catch(() => {});
        }
    }

    function clearMiniPopupVideo(dom) {
        if (!dom?.video) return;
        dom.video.pause();
        dom.video.dataset.src = '';
        dom.video.removeAttribute('src');
        dom.video.load();
        dom.video.style.display = 'none';
        dom.coverWrap?.classList.remove('video-mode');
    }

    function syncMiniPopupVideoWithAudio(forceSeek = false) {
        const dom = ensureMiniPopupDom();
        if (!dom?.video || !currentTrack || currentTrack.type !== 'VIDEO') return;
        if (!isMiniPlayerVisible || !miniPopup || miniPopup.closed) {
            dom.video.pause();
            return;
        }
        const audioTime = isFinite(audio.currentTime) ? audio.currentTime : 0;
        if (forceSeek || Math.abs((dom.video.currentTime || 0) - audioTime) > 0.35) {
            try { dom.video.currentTime = audioTime; } catch (e) { /* ignore */ }
        }
        if (audio.paused) {
            dom.video.pause();
        } else {
            dom.video.play().catch(() => {});
        }
    }

    function syncMiniVideoWithAudio(forceSeek = false) {
        syncMiniInlineVideoWithAudio(forceSeek);
        syncMiniPopupVideoWithAudio(forceSeek);
    }

    function ensureMiniPopupDom() {
        if (!miniPopup || miniPopup.closed) {
            miniPopup = null;
            miniPopupDom = null;
            return null;
        }
        if (miniPopupDom) return miniPopupDom;

        const doc = miniPopup.document;
        miniPopupDom = {
            title: doc.getElementById('mini-popup-title'),
            artist: doc.getElementById('mini-popup-artist'),
            coverWrap: doc.querySelector('.mini-cover-wrap'),
            video: doc.getElementById('mini-popup-video'),
            coverImg: doc.getElementById('mini-popup-cover-img'),
            coverIcon: doc.getElementById('mini-popup-cover-icon'),
            btnPlay: doc.getElementById('mini-popup-play'),
            btnClose: doc.getElementById('mini-popup-close'),
            btnExpand: doc.getElementById('mini-popup-expand'),
            seek: doc.getElementById('mini-popup-seek'),
            currentTime: doc.getElementById('mini-popup-current'),
            totalTime: doc.getElementById('mini-popup-total')
        };

        miniPopupDom.btnClose?.addEventListener('click', () => {
            closeMiniPlayer();
        });
        miniPopupDom.btnExpand?.addEventListener('click', () => {
            closeMiniPlayer();
            openExpandedPlayer();
        });
        miniPopupDom.btnPlay?.addEventListener('click', () => {
            if (!hasPlayableTrack()) return;
            audio.paused ? audio.play() : audio.pause();
        });
        miniPopupDom.seek?.addEventListener('input', () => {
            if (!isFinite(audio.duration)) return;
            const t = audio.duration * parseFloat(miniPopupDom.seek.value) / 100;
            if (miniPopupDom.currentTime) miniPopupDom.currentTime.textContent = fmt(t);
        });
        miniPopupDom.seek?.addEventListener('change', () => {
            if (!isFinite(audio.duration)) return;
            audio.currentTime = audio.duration * parseFloat(miniPopupDom.seek.value) / 100;
            syncExpandedVideoWithAudio(true);
            syncMiniVideoWithAudio(true);
        });

        if (!miniPopup.__kmBound) {
            miniPopup.addEventListener('beforeunload', () => {
                isMiniPlayerVisible = false;
                btnPlayerMini?.classList.remove('btn-active');
                miniPopup = null;
                miniPopupDom = null;
            });
            miniPopup.__kmBound = true;
        }

        return miniPopupDom;
    }

    function ensureMiniPopupWindow() {
        if (miniPopup && !miniPopup.closed) return miniPopup;
        const popup = window.open('', 'kangmusic-mini-player', 'width=420,height=560,resizable=yes,scrollbars=no');
        if (!popup) return null;
        popup.document.open();
        popup.document.write(getMiniPopupMarkup());
        popup.document.close();
        miniPopup = popup;
        miniPopupDom = null;
        ensureMiniPopupDom();
        return popup;
    }

    function updateMiniPopup(track) {
        const dom = ensureMiniPopupDom();
        if (!dom) return;

        if (!track) {
            dom.title && (dom.title.textContent = 'No track');
            dom.artist && (dom.artist.textContent = '-');
            clearMiniPopupVideo(dom);
            if (dom.coverImg) dom.coverImg.style.display = 'none';
            if (dom.coverIcon) {
                dom.coverIcon.style.display = 'flex';
                dom.coverIcon.textContent = AUDIO_ICON;
            }
        } else {
            dom.title && (dom.title.textContent = track.title || 'Unknown title');
            dom.artist && (dom.artist.textContent = track.artist || 'Unknown artist');
            if (track.type === 'VIDEO' && dom.video) {
                if (dom.video.dataset.src !== track.src) {
                    dom.video.src = track.src;
                    dom.video.dataset.src = track.src || '';
                    dom.video.load();
                }
                dom.coverWrap?.classList.add('video-mode');
                dom.video.style.display = 'block';
                if (dom.coverImg) dom.coverImg.style.display = 'none';
                if (dom.coverIcon) dom.coverIcon.style.display = 'none';
                syncMiniPopupVideoWithAudio(true);
            } else if (dom.coverImg && dom.coverIcon) {
                clearMiniPopupVideo(dom);
                if (track.poster && track.poster !== 'null') {
                    dom.coverImg.src = `/stream/${track.poster}`;
                    dom.coverImg.style.display = 'block';
                    dom.coverIcon.style.display = 'none';
                } else {
                    dom.coverImg.style.display = 'none';
                    dom.coverIcon.style.display = 'flex';
                    dom.coverIcon.textContent = track.type === 'VIDEO' ? VIDEO_ICON : AUDIO_ICON;
                }
            }
        }

        if (dom.currentTime) dom.currentTime.textContent = fmt(audio.currentTime || 0);
        if (dom.totalTime) dom.totalTime.textContent = fmt(audio.duration || 0);
        if (dom.seek) {
            dom.seek.value = (isFinite(audio.duration) && audio.duration > 0)
                ? ((audio.currentTime / audio.duration) * 100)
                : 0;
        }
    }

    function syncMiniPlayButton() {
        if (btnMiniPlay) {
            btnMiniPlay.textContent = audio.paused ? MINI_PLAY_ICON : MINI_PAUSE_ICON;
        }
        if (miniPopupDom?.btnPlay) {
            miniPopupDom.btnPlay.textContent = audio.paused ? MINI_PLAY_ICON : MINI_PAUSE_ICON;
        }
    }

    function updateMiniPlayer(track) {
        updateMiniPopup(track);
        if (!miniWindow) {
            syncMiniPlayButton();
            return;
        }

        if (!track) {
            miniTrackTitle && (miniTrackTitle.textContent = 'Chua co bai hat');
            miniTrackArtist && (miniTrackArtist.textContent = '-');
            clearMiniInlineVideo();
            if (miniCoverImg) miniCoverImg.style.display = 'none';
            if (miniCoverIcon) miniCoverIcon.style.display = 'flex';
        } else {
            miniTrackTitle && (miniTrackTitle.textContent = track.title || 'Unknown title');
            miniTrackArtist && (miniTrackArtist.textContent = track.artist || 'Unknown artist');
            if (track.type === 'VIDEO' && miniVideo && miniWindow?.classList.contains('visible')) {
                if (miniVideo.dataset.src !== track.src) {
                    miniVideo.src = track.src;
                    miniVideo.dataset.src = track.src || '';
                    miniVideo.load();
                }
                miniVideo.closest('.player-mini-cover-wrap')?.classList.add('video-mode');
                miniVideo.style.display = 'block';
                if (miniCoverImg) miniCoverImg.style.display = 'none';
                if (miniCoverIcon) miniCoverIcon.style.display = 'none';
                syncMiniInlineVideoWithAudio(true);
            } else if (miniCoverImg && miniCoverIcon) {
                clearMiniInlineVideo();
                if (track.poster && track.poster !== 'null') {
                    miniCoverImg.src = `/stream/${track.poster}`;
                    miniCoverImg.style.display = 'block';
                    miniCoverIcon.style.display = 'none';
                } else {
                    miniCoverImg.style.display = 'none';
                    miniCoverIcon.style.display = 'flex';
                    miniCoverIcon.textContent = track.type === 'VIDEO' ? VIDEO_ICON : AUDIO_ICON;
                }
            }
        }

        if (miniCurrentTime) miniCurrentTime.textContent = fmt(audio.currentTime || 0);
        if (miniTotalTime) miniTotalTime.textContent = fmt(audio.duration || 0);
        if (miniSeekBar) {
            miniSeekBar.value = (isFinite(audio.duration) && audio.duration > 0)
                ? ((audio.currentTime / audio.duration) * 100)
                : 0;
        }
        syncMiniVideoWithAudio();
        syncMiniPlayButton();
    }

    function openMiniPlayer() {
        if (!hasPlayableTrack()) {
            showToast('Hay phat mot bai hat truoc!');
            return;
        }

        closeExpandedPlayer();
        const popup = ensureMiniPopupWindow();
        if (popup) {
            hideInlineMiniPlayer();
            isMiniPlayerVisible = true;
            btnPlayerMini?.classList.add('btn-active');
            updateMiniPlayer(currentTrack);
            syncMiniVideoWithAudio(true);
            popup.focus();
            return;
        }

        if (!miniWindow) return;
        isMiniPlayerVisible = true;
        showInlineMiniPlayer();
        btnPlayerMini?.classList.add('btn-active');
        updateMiniPlayer(currentTrack);
        syncMiniVideoWithAudio(true);
        showToast('Popup bi chan. Dang mo mini player trong trang.');
    }

    function closeMiniPlayer() {
        isMiniPlayerVisible = false;
        btnPlayerMini?.classList.remove('btn-active');
        hideInlineMiniPlayer();
        clearMiniInlineVideo();
        clearMiniPopupVideo(miniPopupDom);

        const popupRef = miniPopup;
        miniPopup = null;
        miniPopupDom = null;
        if (popupRef && !popupRef.closed) {
            popupRef.close();
        }
    }

    function toggleMiniPlayer() {
        if (isMiniPlayerVisible) closeMiniPlayer();
        else openMiniPlayer();
    }

    function saveQueue() {
        try {
            localStorage.setItem('km_queue', JSON.stringify(queue));
            localStorage.setItem('km_queue_index', queueIndex);
        } catch (e) { /* quota exceeded — ignore */ }
    }
    function loadQueue() {
        try {
            const raw = JSON.parse(localStorage.getItem('km_queue') || '[]');
            if (!Array.isArray(raw)) return [];
            return raw
                .filter(t => t && typeof t === 'object' && typeof t.src === 'string' && t.src.trim() !== '')
                .map(t => ({
                    id: t.id || '',
                    title: t.title || 'Unknown title',
                    artist: t.artist || 'Unknown artist',
                    src: t.src,
                    type: t.type === 'VIDEO' ? 'VIDEO' : 'AUDIO',
                    genre: t.genre || '',
                    emotion: t.emotion || '',
                    lyrics: t.lyrics || '',
                    poster: t.poster || ''
                }));
        } catch {
            return [];
        }
    }
    function loadQueueIndex() {
        const v = parseInt(localStorage.getItem('km_queue_index') || '0', 10);
        return isNaN(v) ? 0 : v;
    }

    // ── Format time ────────────────────────────────────────────────────────
    function fmt(sec) {
        if (!isFinite(sec) || sec < 0) return '0:00';
        const m = Math.floor(sec / 60);
        const s = Math.floor(sec % 60);
        return `${m}:${s.toString().padStart(2, '0')}`;
    }

    // ── Play a track object ────────────────────────────────────────────────
    function playTrack(track) {
        if (!track) return;
        currentTrack = track;

        container.classList.remove('hidden');
        container.style.display = 'flex';

        titleEl.textContent  = track.title;
        artistEl.textContent = track.artist;
        
        const posterImg = document.getElementById('player-poster-img');
        if (track.poster && track.poster !== 'null') {
            posterImg.src = '/stream/' + track.poster;
            posterImg.style.display = 'block';
            artworkIcon.style.display = 'none';
        } else {
            posterImg.style.display = 'none';
            artworkIcon.style.display = 'flex';
            artworkIcon.textContent = track.type === 'VIDEO' ? VIDEO_ICON : AUDIO_ICON;
        }

        const infoLink = document.getElementById('player-info-link');
        if (infoLink && track.id) {
            infoLink.href = '/track/' + track.id;
        }

        const playerAddPlaylistBtn = document.getElementById('player-add-playlist');
        if (playerAddPlaylistBtn && track.id) {
            playerAddPlaylistBtn.dataset.mediaId = track.id;
        }

        audio.src = track.src;
        audio.load();
        audio.play().catch(e => console.warn('Autoplay blocked:', e));

        if (track.id) {
            // CRIT-2 FIX: Include CSRF token header on POST requests
            fetch(`/api/play/${track.id}`, {
                method: 'POST',
                headers: { 'X-XSRF-TOKEN': getCsrfToken() }
            }).catch(() => {});
        }

        // Cập nhật Right Sidebar — Now Playing view
        updateNowPlayingSidebar(track);
        if (lyricsOverlay?.classList.contains('visible')) {
            updateLyrics(track);
        }
        if (isExpandedPlayerVisible) {
            updateExpandedPlayer(track);
        }
        if (isMiniPlayerVisible) {
            updateMiniPlayer(track);
        }

        renderQueuePanel();
        saveQueue();
    }

    function updateNowPlayingSidebar(track) {
        const rsTitle = document.getElementById('rs-track-title');
        const rsArtist = document.getElementById('rs-track-artist');
        const rsCreditArtist = document.getElementById('rs-credit-artist');
        if (rsTitle) { rsTitle.textContent = track.title; rsTitle.href = '/track/' + (track.id || '#'); }
        if (rsArtist) rsArtist.textContent = track.artist;
        if (rsCreditArtist) rsCreditArtist.textContent = track.artist;

        const rsGlow = document.getElementById('rs-cover-glow');
        const rsIcon = document.getElementById('rs-cover-icon');
        const rsPoster = document.getElementById('rs-poster-img');
        if (track.poster && track.poster !== 'null') {
            rsGlow.style.backgroundImage = `url('/stream/${track.poster}')`;
            rsGlow.style.opacity = '1';
            if (rsPoster) {
                rsPoster.src = `/stream/${track.poster}`;
                rsPoster.style.display = 'block';
            }
            rsIcon.style.display = 'none';
        } else {
            rsGlow.style.backgroundImage = 'none';
            rsGlow.style.opacity = '0.4';
            if (rsPoster) rsPoster.style.display = 'none';
            rsIcon.style.display = 'flex';
            rsIcon.textContent = track.type === 'VIDEO' ? VIDEO_ICON : AUDIO_ICON;
        }

        // Update "đang phát từ" label
        const rsFrom = document.getElementById('rs-playing-from');
        if (rsFrom) rsFrom.textContent = 'Danh sách chờ';
    }

    // ── Play by queue index ────────────────────────────────────────────────
    function playAtIndex(idx) {
        if (queue.length === 0) return;
        queueIndex = ((idx % queue.length) + queue.length) % queue.length;
        playTrack(queue[queueIndex]);
        saveQueue();
    }

    // ── Add track to queue ─────────────────────────────────────────────────
    function addToQueue(track) {
        queue.push(track);
        saveQueue();
        renderQueuePanel();
        showToast('Đã thêm vào danh sách chờ');
    }

    async function playPlaylistFromLibrary(playlistId) {
        if (!playlistId) return;
        try {
            const res = await fetch(`/api/playlists/${playlistId}/tracks`);
            if (!res.ok) throw new Error("playlist_fetch_failed");
            const data = await res.json();
            if (!Array.isArray(data) || data.length === 0) {
                showToast("Playlist chưa có bài hát để phát");
                return;
            }

            const nextQueue = data
                .map(t => ({
                    id: t.id,
                    title: t.title || "Unknown title",
                    artist: t.artist || "Unknown artist",
                    src: t.src,
                    type: t.type || "AUDIO",
                    genre: t.genre || "",
                    emotion: t.emotion || "",
                    lyrics: t.lyrics || "",
                    poster: t.poster || ""
                }))
                .filter(t => !!t.src);

            if (nextQueue.length === 0) {
                showToast("Playlist chưa có bài hát để phát");
                return;
            }

            queue = nextQueue;
            queueIndex = 0;
            saveQueue();
            renderQueuePanel();
            playTrack(queue[0]);
            showToast("Đang phát playlist");
        } catch (e) {
            showToast("Không thể phát playlist này");
        }
    }

    // CODE-8 FIX: Only one km:addToQueue listener — the deduplicated one below (line ~656).
    // The duplicate without dedup check has been removed.

    function trackFromBtn(btn) {
        return {
            id:      btn.dataset.id,
            title:   btn.dataset.title,
            artist:  btn.dataset.artist,
            src:     btn.dataset.src,
            type:    btn.dataset.type || 'AUDIO',
            genre:   btn.dataset.genre || '',
            emotion: btn.dataset.emotion || '',
            lyrics:  btn.dataset.lyrics || '',
            poster:  btn.dataset.poster || ''
        };
    }

    // ── Event delegation — play buttons ───────────────────────────────────
    document.body.addEventListener('click', e => {
        const playBtn = e.target.closest('.media-play-btn, #btn-track-play');
        if (!playBtn) return;

        const track = trackFromBtn(playBtn);
        if (!track.src) return;

        if (currentTrack && currentTrack.src === track.src) {
            audio.paused ? audio.play() : audio.pause();
            return;
        }

        const existingIdx = queue.findIndex(t => t.src === track.src);
        if (existingIdx >= 0) {
            queueIndex = existingIdx;
        } else {
            queue.splice(queueIndex + 1, 0, track);
            queueIndex = queueIndex + 1;
        }
        saveQueue();
        playTrack(track);
    });

    // ── Sidebar Folder Toggle ─────────────────────────────────────────────
    document.body.addEventListener('click', e => {
        const folder = e.target.closest('.library-item.is-folder');
        if (!folder) return;

        const folderId = folder.dataset.id;
        const subItems = document.getElementById('folder-content-' + folderId);
        const chevron  = folder.querySelector('.folder-chevron');

        if (subItems) {
            const isHidden = subItems.classList.contains('hidden');
            subItems.classList.toggle('hidden');
            if (chevron) {
                chevron.style.transform = isHidden ? 'rotate(90deg)' : 'rotate(0deg)';
            }
        }
    });

    // ── Library Item Navigation ──────────────────────────────────────────
    document.body.addEventListener('click', e => {
        const item = e.target.closest('.library-item[data-href]');
        if (!item) return;

        // Skip navigation if only the chevron was clicked
        if (e.target.closest('.folder-chevron')) return;

        const href = item.dataset.href;
        if (href) {
            if (!navigateMainContent(href)) {
                window.location.href = href;
            }
        }
    });

    // Nút "Thêm vào hàng chờ"
    document.body.addEventListener('click', e => {
        const qBtn = e.target.closest('[data-action="queue"], #btn-track-queue');
        if (!qBtn) return;
        e.stopPropagation();

        if (qBtn.id === 'btn-track-queue') {
            document.body.dispatchEvent(new CustomEvent('km:addToQueue', {
                detail: trackFromBtn(qBtn)
            }));
            return;
        }

        const card = qBtn.closest('.media-card');
        const playBtn = card?.querySelector('.media-play-btn');
        if (playBtn) addToQueue(trackFromBtn(playBtn));
    });

    document.body.addEventListener("click", e => {
        const pBtn = e.target.closest("[data-action=\"play-playlist\"]");
        if (!pBtn) return;
        e.preventDefault();
        e.stopPropagation();
        playPlaylistFromLibrary(pBtn.dataset.playlistId);
    });

    // NÃºt "PhÃ¡t táº¥t cáº£" á»Ÿ playlist/library
    document.body.addEventListener('click', e => {
        const playAllBtn = e.target.closest('#btn-play-all, #btn-play-liked');
        if (!playAllBtn) return;
        e.preventDefault();

        const rows = document.querySelectorAll('.track-row');
        if (!rows.length) return;

        rows[0].querySelector('.track-play-btn.media-play-btn')?.click();
        for (let i = 1; i < rows.length; i++) {
            const btn = rows[i].querySelector('.track-play-btn.media-play-btn');
            if (!btn) continue;
            document.body.dispatchEvent(new CustomEvent('km:addToQueue', {
                detail: trackFromBtn(btn)
            }));
        }
    });

    // Track details tabs
    document.body.addEventListener('click', e => {
        const tabBtn = e.target.closest('.track-tab-btn');
        if (!tabBtn) return;

        const tabsContainer = tabBtn.closest('.track-tabs-container');
        if (!tabsContainer) return;

        tabsContainer.querySelectorAll('.track-tab-btn').forEach(btn => btn.classList.remove('active'));
        tabsContainer.querySelectorAll('.track-tab-content').forEach(tab => tab.classList.remove('active'));
        tabBtn.classList.add('active');

        const target = document.getElementById('tab-' + tabBtn.dataset.tab);
        if (target) target.classList.add('active');
    });

    // ── Controls ───────────────────────────────────────────────────────────
    btnPlay.addEventListener('click', () => {
        if (!currentTrack) return;
        audio.paused ? audio.play() : audio.pause();
    });

    btnPrev.addEventListener('click', () => {
        if (audio.currentTime > 3) { audio.currentTime = 0; return; }
        playAtIndex(queueIndex - 1);
    });

    btnNext.addEventListener('click', () => playNext());

    btnShuffle.addEventListener('click', () => {
        shuffle = !shuffle;
        btnShuffle.classList.toggle('btn-active', shuffle);
        showToast(shuffle ? 'Bật trộn bài' : 'Tắt trộn bài');
    });

    function updateRepeatButtonUi() {
        const labels = {
            none: 'Lap: Tat',
            one: 'Lap: 1 bai',
            all: 'Lap: Tat ca'
        };
        btnRepeat.classList.toggle('btn-active', repeatMode !== 'none');
        btnRepeat.dataset.mode = repeatMode;
        btnRepeat.title = labels[repeatMode] || 'Lap lai';
        btnRepeat.setAttribute('aria-label', labels[repeatMode] || 'Repeat');

        const icon = btnRepeat.querySelector('svg');
        if (icon) icon.style.opacity = repeatMode === 'none' ? '0.6' : '1';
    }

    updateRepeatButtonUi();

    btnRepeat.addEventListener('click', () => {
        const modes = ['none', 'one', 'all'];
        repeatMode = modes[(modes.indexOf(repeatMode) + 1) % modes.length];
        const labels = { none: 'Tắt lặp', one: 'Lặp 1 bài', all: 'Lặp playlist' };
        updateRepeatButtonUi();
        showToast(labels[repeatMode]);
    });

    // ── RIGHT SIDEBAR TAB NAVIGATION ─────────────────────────────────────
    function setSidebarView(view) {
        if (!rightSidebar) return;

        // Remove old view classes
        rightSidebar.classList.remove('view-playing', 'view-queue', 'view-lyrics');
        rightSidebar.classList.add('view-' + view);

        // Update tab active states
        document.querySelectorAll('.rs-tab-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.view === view);
        });

        // Load lyrics if needed
        if (view === 'lyrics' && currentTrack) {
            updateLyrics(currentTrack);
        }

        // Render queue if needed
        if (view === 'queue') {
            renderQueuePanel();
        }
    }

    // Tab button click handlers
    document.querySelectorAll('.rs-tab-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            setSidebarView(btn.dataset.view);
        });
    });

    // Player bar queue button → goes to queue view
    btnQueue?.addEventListener('click', (e) => {
        e.stopPropagation();
        rightSidebar?.classList.remove('hidden', 'collapsed');
        setSidebarView('queue');
    });

    btnPlayerExpand?.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleExpandedPlayer();
    });

    btnPlayerMini?.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleMiniPlayer();
    });

    rsBtnFullscreen?.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleExpandedPlayer();
    });

    // Lyrics overlay helpers
    function openLyricsOverlay() {
        if (!lyricsOverlay) return;
        if (!currentTrack) {
            showToast('Hay phat mot bai hat truoc!');
            return;
        }
        closeExpandedPlayer();
        updateLyrics(currentTrack);
        lyricsOverlay.classList.add('visible');
    }

    function closeLyricsOverlay() {
        lyricsOverlay?.classList.remove('visible');
    }

    btnLyrics?.addEventListener('click', (e) => {
        e.stopPropagation();
        openLyricsOverlay();
    });

    // Lyrics overlay close handlers
    btnCloseLyricsOverlay?.addEventListener('click', (e) => {
        e.stopPropagation();
        closeLyricsOverlay();
    });

    lyricsOverlay?.addEventListener('click', (e) => {
        if (e.target === lyricsOverlay) {
            closeLyricsOverlay();
        }
    });

    document.addEventListener('keydown', (e) => {
        if (e.key !== 'Escape') return;
        if (lyricsOverlay?.classList.contains('visible')) {
            closeLyricsOverlay();
            return;
        }
        if (isExpandedPlayerVisible) {
            closeExpandedPlayer();
            return;
        }
        if (isMiniPlayerVisible) {
            closeMiniPlayer();
        }
    });

    // Player bar Now Playing button
    const btnNowPlaying = document.getElementById('btn-now-playing');
    btnNowPlaying?.addEventListener('click', (e) => {
        e.stopPropagation();
        rightSidebar?.classList.remove('hidden', 'collapsed');
        setSidebarView('playing');
    });

    btnExpandedClose?.addEventListener('click', (e) => {
        e.stopPropagation();
        closeExpandedPlayer();
    });

    btnExpandedMini?.addEventListener('click', (e) => {
        e.stopPropagation();
        closeExpandedPlayer();
        openMiniPlayer();
    });

    expandedOverlay?.addEventListener('click', (e) => {
        if (e.target === expandedOverlay) {
            closeExpandedPlayer();
        }
    });
    expandedScroll?.addEventListener('scroll', () => {
        syncExpandedScrollState();
    }, { passive: true });

    expandedQueuePreview?.addEventListener('click', (e) => {
        const row = e.target.closest('.player-expanded-queue-item');
        if (!row) return;
        const idx = parseInt(row.dataset.idx || '-1', 10);
        if (Number.isInteger(idx) && idx >= 0) {
            playAtIndex(idx);
        }
    });

    btnMiniClose?.addEventListener('click', (e) => {
        e.stopPropagation();
        closeMiniPlayer();
    });

    btnMiniExpand?.addEventListener('click', (e) => {
        e.stopPropagation();
        closeMiniPlayer();
        openExpandedPlayer();
    });

    btnMiniPlay?.addEventListener('click', (e) => {
        e.stopPropagation();
        if (!hasPlayableTrack()) return;
        audio.paused ? audio.play() : audio.pause();
    });

    // Close button → hides sidebar completely
    const btnCloseSidebar = document.getElementById('btn-close-right-sidebar');
    btnCloseSidebar?.addEventListener('click', (e) => {
        e.stopPropagation();
        rightSidebar?.classList.add('collapsed');
    });

    // OPEN WHEN COLLAPSED
    rightSidebar?.addEventListener('click', (e) => {
        if (rightSidebar.classList.contains('collapsed')) {
            rightSidebar.classList.remove('collapsed');
        }
    });

    // ── Volume ─────────────────────────────────────────────────────────────
    volumeSlider.addEventListener('input', () => {
        audio.volume = parseFloat(volumeSlider.value);
    });

    miniSeekBar?.addEventListener('input', () => {
        if (!isFinite(audio.duration)) return;
        const t = audio.duration * parseFloat(miniSeekBar.value) / 100;
        if (miniCurrentTime) miniCurrentTime.textContent = fmt(t);
    });
    miniSeekBar?.addEventListener('change', () => {
        if (!isFinite(audio.duration)) return;
        audio.currentTime = audio.duration * parseFloat(miniSeekBar.value) / 100;
        syncExpandedVideoWithAudio(true);
        syncMiniVideoWithAudio(true);
    });

    // ── Seek bar ───────────────────────────────────────────────────────────
    seekBar.addEventListener('mousedown', () => { isDragging = true; });
    seekBar.addEventListener('touchstart', () => { isDragging = true; });
    seekBar.addEventListener('input', () => {
        if (isFinite(audio.duration)) {
            currentTimeEl.textContent = fmt(audio.duration * seekBar.value / 100);
        }
    });
    seekBar.addEventListener('change', () => {
        if (isFinite(audio.duration)) {
            audio.currentTime = audio.duration * seekBar.value / 100;
        }
        isDragging = false;
        syncExpandedVideoWithAudio(true);
        syncMiniVideoWithAudio(true);
    });

    // ── Audio events ───────────────────────────────────────────────────────
    audio.addEventListener('play',  () => {
        btnPlay.innerHTML = PAUSE_SVG;
        syncMiniPlayButton();
        syncExpandedVideoWithAudio(true);
        syncMiniVideoWithAudio(true);
    });
    audio.addEventListener('pause', () => {
        btnPlay.innerHTML = PLAY_SVG;
        syncMiniPlayButton();
        syncExpandedVideoWithAudio();
        syncMiniVideoWithAudio();
    });

    audio.addEventListener('timeupdate', () => {
        if (isDragging || !isFinite(audio.duration)) return;
        const pct = (audio.currentTime / audio.duration) * 100;
        seekBar.value = pct;
        currentTimeEl.textContent = fmt(audio.currentTime);
        totalTimeEl.textContent   = fmt(audio.duration);
        if (isMiniPlayerVisible) {
            updateMiniPlayer(currentTrack);
        }
        syncExpandedVideoWithAudio();
        syncMiniVideoWithAudio();
        
        // Save current time to restore if page reloads
        try {
            localStorage.setItem('km_currentTime', audio.currentTime);
            localStorage.setItem('km_currentQueueIndex', queueIndex);
        } catch (e) {}
    });

    audio.addEventListener('loadedmetadata', () => {
        totalTimeEl.textContent = fmt(audio.duration);
        seekBar.value = 0;
        if (isMiniPlayerVisible) {
            updateMiniPlayer(currentTrack);
        }
        syncExpandedVideoWithAudio(true);
        syncMiniVideoWithAudio(true);
    });

    audio.addEventListener('ended', () => playNext());

    // ── Auto-next logic ────────────────────────────────────────────────────
    function playNext() {
        if (repeatMode === 'one') {
            audio.currentTime = 0;
            audio.play();
            return;
        }

        if (shuffle && queue.length > 1) {
            let next;
            do { next = Math.floor(Math.random() * queue.length); }
            while (next === queueIndex);
            playAtIndex(next);
            return;
        }

        const nextIdx = queueIndex + 1;
        if (nextIdx < queue.length) {
            playAtIndex(nextIdx);
        } else if (repeatMode === 'all') {
            playAtIndex(0);
        } else {
            autoFillQueue();
        }
    }

    // ── Auto-fill queue ───────────────────────────────────────────────────
    async function autoFillQueue() {
        const genre = currentTrack?.genre;
        const id    = currentTrack?.id;
        if (!genre && !id) return;

        const url = id
            ? `/api/similar/${id}?genre=${genre || ''}&emotion=${currentTrack?.emotion || ''}&limit=10`
            : `/api/genre/${genre}?limit=10`;

        try {
            const res = await fetch(url);
            if (!res.ok) return;
            const items = await res.json();
            const newTracks = items
                .filter(i => !queue.find(q => q.id == i.id))
                .map(i => ({
                    id:      i.id,
                    title:   i.title,
                    artist:  i.artist,
                    // S3/S4 FIX: DTO provides 'src' directly — no raw fileName exposed
                    src:     i.src,
                    poster:  i.posterSrc || '',
                    type:    i.type,
                    genre:   i.genre || '',
                    emotion: i.emotionLabel || ''
                }));

            if (newTracks.length > 0) {
                queue.push(...newTracks);
                saveQueue();
                renderQueuePanel();
                playAtIndex(queueIndex + 1);
                showToast(`Tự động thêm ${newTracks.length} bài cùng thể loại`);
            }
        } catch (e) {
            console.warn('Auto-fill failed:', e);
        }
    }

    // ── Queue panel render ─────────────────────────────────────────────────
    function createQueueItem(track, idx, isNow) {
        const item = document.createElement('div');
        item.className = isNow ? 'queue-now-track' : 'queue-item';
        const safeTrack = track && typeof track === 'object' ? track : {};
        const emoji = safeTrack.type === 'VIDEO' ? VIDEO_ICON : AUDIO_ICON;
        const thumbHTML = safeTrack.poster 
            ? `<img src="/stream/${safeTrack.poster}" style="width:100%; height:100%; object-fit:cover; border-radius:4px; display:block;">` 
            : `<span>${emoji}</span>`;
        item.innerHTML = `
            <div class="queue-item-thumb" style="display:flex; justify-content:center; align-items:center; overflow:hidden;">${thumbHTML}</div>
            <div class="queue-item-info">
                <div class="queue-item-title">${escHtml(safeTrack.title || 'Unknown title')}</div>
                <div class="queue-item-artist">${escHtml(safeTrack.artist || 'Unknown artist')}</div>
            </div>
            <button class="queue-item-more" title="Tùy chọn">···</button>
            ${isNow ? '' : `<button class="queue-item-remove" data-idx="${idx}" title="Xóa">✕</button>`}
        `;
        item.addEventListener('click', e => {
            if (e.target.closest('.queue-item-remove')) {
                const rmIdx = parseInt(e.target.closest('.queue-item-remove').dataset.idx);
                queue.splice(rmIdx, 1);
                if (queueIndex >= rmIdx && queueIndex > 0) queueIndex--;
                saveQueue();
                renderQueuePanel();
            } else if (!e.target.closest('.queue-item-more')) {
                if (!isNow) playAtIndex(idx);
            }
        });
        return item;
    }

    function renderQueuePanel() {
        const nowSection  = document.getElementById('queue-now-playing-section');
        const nowItem     = document.getElementById('queue-now-item');
        const nextSection = document.getElementById('queue-next-section');
        const queueList   = document.getElementById('queue-list-sidebar');
        const emptyState  = document.getElementById('queue-empty-state');

        if (!nowSection || !nowItem || !nextSection || !queueList || !emptyState) {
            return;
        }

        // Guard against stale/corrupted queue state (e.g. saved index out of range).
        if (!Array.isArray(queue)) queue = [];
        const beforeLen = queue.length;
        queue = queue.filter(t => t && typeof t === 'object' && typeof t.src === 'string' && t.src.trim() !== '');
        if (queue.length !== beforeLen) {
            saveQueue();
        }
        if (!Number.isInteger(queueIndex)) {
            const parsed = parseInt(queueIndex, 10);
            queueIndex = Number.isNaN(parsed) ? 0 : parsed;
        }

        if (queue.length === 0) {
            queueIndex = 0;
            nowSection.style.display  = 'none';
            nextSection.style.display = 'none';
            emptyState.style.display  = 'block';
            return;
        }

        if (queueIndex < 0 || queueIndex >= queue.length) {
            queueIndex = 0;
            saveQueue();
        }

        emptyState.style.display = 'none';

        // Đang phát
        if (queue[queueIndex]) {
            nowSection.style.display = 'block';
            nowItem.innerHTML = '';
            nowItem.appendChild(createQueueItem(queue[queueIndex], queueIndex, true));
        } else {
            nowSection.style.display = 'none';
        }

        // Tiếp theo
        const upcoming = queue.slice(queueIndex + 1);
        if (upcoming.length > 0) {
            nextSection.style.display = 'block';
            queueList.innerHTML = '';
            upcoming.forEach((track, i) => {
                queueList.appendChild(createQueueItem(track, queueIndex + 1 + i, false));
            });
        } else {
            nextSection.style.display = 'none';
        }

        // Final safety: never leave queue view fully blank.
        if (nowSection.style.display === 'none' && nextSection.style.display === 'none') {
            emptyState.style.display = 'block';
        }
        if (isExpandedPlayerVisible) {
            updateExpandedPlayer(currentTrack);
        }
    }

    // ── Lyrics update ──────────────────────────────────────────────────────
    function updateLyrics(track) {
        if (!lyricsOverlayContent) return;
        if (lyricsOverlayTitle) {
            lyricsOverlayTitle.textContent = track?.title ? `Lyrics - ${track.title}` : 'Lyrics';
        }
        if (track.lyrics && track.lyrics.trim()) {
            lyricsOverlayContent.textContent = track.lyrics;
        } else {
            lyricsOverlayContent.innerHTML = '<div class="lyrics-overlay-empty">Kh&#244;ng c&#243; l&#7901;i b&#224;i h&#225;t</div>';
        }
    }

    // ── Toast notification ────────────────────────────────────────────────
    function showToast(msg) {
        let t = document.getElementById('km-toast');
        if (!t) {
            t = document.createElement('div');
            t.id = 'km-toast';
            t.className = 'km-toast';
            document.body.appendChild(t);
        }
        t.textContent = msg;
        t.classList.add('show');
        clearTimeout(t._timer);
        t._timer = setTimeout(() => t.classList.remove('show'), 2000);
    }

    function escHtml(s) {
        return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    }

    // ── Khôi phục queue từ localStorage ───────────────────────────────────
    function restoreQueueState() {
        if (queue.length > 0 && queue[queueIndex]) {
            const track = queue[queueIndex];
            container.classList.remove('hidden');
            container.style.display = 'flex';
            titleEl.textContent  = track.title;
            const posterImg = document.getElementById('player-poster-img');
            if (track.poster && track.poster !== 'null') {
                if (posterImg) {
                    posterImg.src = '/stream/' + track.poster;
                    posterImg.style.display = 'block';
                }
                artworkIcon.style.display = 'none';
            } else {
                if (posterImg) posterImg.style.display = 'none';
                artworkIcon.style.display = 'flex';
                artworkIcon.textContent = track.type === 'VIDEO' ? VIDEO_ICON : AUDIO_ICON;
            }
            
            const infoLink = document.getElementById('player-info-link');
            if (infoLink && track.id) infoLink.href = '/track/' + track.id;
            
            audio.src = track.src;
            audio.load();

            try {
                const savedIdx = parseInt(localStorage.getItem('km_currentQueueIndex') || '-1');
                if (savedIdx === queueIndex) {
                    const savedTime = parseFloat(localStorage.getItem('km_currentTime'));
                    if (!isNaN(savedTime)) {
                        audio.currentTime = savedTime;
                        currentTimeEl.textContent = fmt(audio.currentTime);
                        if (track.duration) {
                            seekBar.value = (audio.currentTime / track.duration) * 100;
                        }
                    }
                }
            } catch (e) {}

            totalTimeEl.textContent = track.duration ? fmt(track.duration) : '0:00';
            currentTrack = track;
            updateNowPlayingSidebar(track);
        }
    }

    restoreQueueState();
    renderQueuePanel();
    updateExpandedPlayer(currentTrack);
    updateMiniPlayer(currentTrack);

    // ── Scroll listeners ───────────────────────────────────────────────────
    function initContentAreaScroll() {
        const contentArea = document.querySelector('.content-area');
        if (!contentArea) return;
        if (contentArea.dataset.kmScrollInit === '1') return;

        const updateHeaderState = () => {
            const contentHeader = contentArea.querySelector('.content-header');
            if (contentHeader) {
                contentHeader.classList.toggle('header-scrolled', contentArea.scrollTop > 20);
            }
        };

        contentArea.addEventListener('scroll', updateHeaderState, { passive: true });
        contentArea.dataset.kmScrollInit = '1';
        updateHeaderState();
    }

    initContentAreaScroll();

    // ── Like button trong player bar ──────────────────────────────────────
    document.body.addEventListener('click', async e => {
        const trackLikeBtn = e.target.closest('#btn-track-like');
        if (!trackLikeBtn) return;
        e.preventDefault();

        const mediaId = trackLikeBtn.dataset.id;
        if (!mediaId) return;
        if (trackLikeBtn.dataset.loading === 'true') return;
        trackLikeBtn.dataset.loading = 'true';

        try {
            const fd = new URLSearchParams();
            fd.append('mediaItemId', mediaId);
            const res = await fetch('/api/library/toggle', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-XSRF-TOKEN': getCsrfToken()
                },
                body: fd
            });
            if (res.status === 401) { showToast('Hay dang nhap de yeu thich!'); return; }
            if (!res.ok) { showToast('Da co loi xay ra'); return; }

            const data = await res.json();
            trackLikeBtn.classList.toggle('liked', data.liked);
            if (currentTrack && String(currentTrack.id) === String(mediaId)) {
                btnLike?.classList.toggle('liked', data.liked);
                document.getElementById('rs-btn-like')?.classList.toggle('liked', data.liked);
            }
            showToast(data.message || 'Da cap nhat yeu thich');
        } catch {
            showToast('Khong the ket noi server');
        } finally {
            trackLikeBtn.removeAttribute('data-loading');
        }
    });

    btnLike?.addEventListener('click', async () => {
        if (!currentTrack?.id) {
            showToast('Hãy phát một bài hát trước!');
            return;
        }
        try {
            const fd = new URLSearchParams();
            fd.append('mediaItemId', currentTrack.id);
            const res = await fetch('/api/library/toggle', {
                method: 'POST',
                // CRIT-2 FIX: Include CSRF token on all state-changing POST calls
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-XSRF-TOKEN': getCsrfToken()
                },
                body: fd
            });
            if (res.status === 401) { showToast('Hãy đăng nhập để yêu thích!'); return; }
            if (!res.ok) { showToast('Đã có lỗi xảy ra'); return; }
            const data = await res.json();
            btnLike.classList.toggle('liked', data.liked);
            // Sync like button in sidebar
            const rsBtnLike = document.getElementById('rs-btn-like');
            if (rsBtnLike) rsBtnLike.classList.toggle('liked', data.liked);
            const trackLikeBtn = document.getElementById('btn-track-like');
            if (trackLikeBtn && trackLikeBtn.dataset.id == currentTrack.id) {
                trackLikeBtn.classList.toggle('liked', data.liked);
            }
            showToast(data.message);
        } catch {
            showToast('Không thể kết nối server');
        }
    });

    // ── rs-btn-like (sidebar like) ─────────────────────────────────────────
    document.getElementById('rs-btn-like')?.addEventListener('click', () => {
        btnLike?.click();
    });

    // ── Cập nhật trạng thái tim khi bài thay đổi ─────────────────────────
    audio.addEventListener('play', async () => {
        if (!currentTrack?.id || !btnLike) return;
        try {
            const res = await fetch(`/api/library/check/${currentTrack.id}`);
            if (res.ok) {
                const { liked } = await res.json();
                btnLike.classList.toggle('liked', liked);
                const rsBtnLike = document.getElementById('rs-btn-like');
                if (rsBtnLike) rsBtnLike.classList.toggle('liked', liked);
                const trackLikeBtn = document.getElementById('btn-track-like');
                if (trackLikeBtn && trackLikeBtn.dataset.id == currentTrack.id) {
                    trackLikeBtn.classList.toggle('liked', liked);
                }
            }
        } catch { /* ignore */ }
    });

    // ── Custom event: km:addToQueue (CODE-8 FIX: single deduplicated listener) ──
    document.body.addEventListener('km:addToQueue', e => {
        const track = e.detail;
        if (track && !queue.find(q => q.src === track.src)) {
            queue.push(track);
            saveQueue();
            renderQueuePanel();
            showToast('Đã thêm vào danh sách chờ');
        }
    });

});

/* ============================================================ */
/* PLAYLIST MODAL                                               */
/* ============================================================ */
(function() {
    let pendingMediaId = null;
    function getEl(id) { return document.getElementById(id); }
    function openModal(mediaId) {
        pendingMediaId = mediaId;
        const overlay = getEl('playlist-modal-overlay');
        const list    = getEl('playlist-modal-list');
        if (!overlay || !list) return;
        overlay.classList.remove('hidden');
        list.innerHTML = '<div style="text-align:center;color:#b3b3b3;padding:20px;">Đang tải...</div>';
        fetch('/api/my-playlists')
            .then(r => r.ok ? r.json() : Promise.reject(r.status))
            .then(pls => renderList(list, pls))
            .catch(err => {
                list.innerHTML = err === 401
                    ? '<div style="text-align:center;color:#b3b3b3;padding:20px;">Hãy đăng nhập để dùng playlist!</div>'
                    : '<div style="text-align:center;color:#b3b3b3;padding:20px;">Lỗi tải danh sách.</div>';
            });
    }
    function closeModal() {
        getEl('playlist-modal-overlay')?.classList.add('hidden');
        pendingMediaId = null;
    }
    function renderList(list, pls) {
        if (!pls.length) {
            list.innerHTML = '<div class="playlist-modal-empty">Chưa có playlist nào.<br><a href="/playlists" style="color:#1db954;">Tạo playlist mới</a></div>';
            return;
        }
        list.innerHTML = '';
        pls.forEach(pl => {
            const el = document.createElement('div');
            el.className = 'playlist-modal-item';
            el.innerHTML = '<span class="playlist-modal-emoji">' + pl.emoji + '</span><span class="playlist-modal-name">' + pl.name + '</span>';
            el.addEventListener('click', () => doAdd(pl.id));
            list.appendChild(el);
        });
    }
    async function doAdd(playlistId) {
        if (!pendingMediaId) return;
        const mediaIdToSend = pendingMediaId;
        closeModal();
        try {
            const fd = new URLSearchParams();
            fd.append('mediaItemId', mediaIdToSend);
            const res  = await fetch('/playlists/' + playlistId + '/add', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-XSRF-TOKEN': getCsrfToken() 
                },
                body: fd
            });
            const data = await res.json();
            toast(data.message || (res.ok ? 'Đã thêm vào playlist!' : 'Lỗi thêm bài hát'));
        } catch { toast('Không thể thêm vào playlist.'); }
    }
    function toast(msg) {
        let t = getEl('km-toast');
        if (!t) { t = document.createElement('div'); t.id='km-toast'; t.className='km-toast'; document.body.appendChild(t); }
        t.textContent = msg; t.classList.add('show');
        clearTimeout(t._timer); t._timer = setTimeout(() => t.classList.remove('show'), 2200);
    }
    document.body.addEventListener('click', e => {
        const btn = e.target.closest('[data-action="add-to-playlist"]');
        if (btn) { e.stopPropagation(); openModal(btn.dataset.mediaId); return; }
        const overlay = getEl('playlist-modal-overlay');
        if (overlay && e.target === overlay) closeModal();
    });
    document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });
    document.body.addEventListener('click', e => { if (e.target.closest('#btn-close-playlist-modal')) closeModal(); });

    // ── Library Sidebar Toggle ─────────────────────────────────────────────
    const librarySidebar   = document.getElementById('library-sidebar');
    const btnToggleLibrary = document.getElementById('btn-toggle-library');

    if (btnToggleLibrary && librarySidebar) {
        const savedState = localStorage.getItem('km_library_state') || 'default';
        if (savedState === 'collapsed') librarySidebar.classList.add('collapsed');

        btnToggleLibrary.addEventListener('click', () => {
            librarySidebar.classList.toggle('collapsed');
            librarySidebar.classList.remove('expanded');
            localStorage.setItem('km_library_state', librarySidebar.classList.contains('collapsed') ? 'collapsed' : 'default');
        });
    }

    // ── Library Expand (Grid View) ─────────────────────────────────────────
    const btnExpandLibrary = document.getElementById('btn-expand-library');
    if (btnExpandLibrary && librarySidebar) {
        const mainLayout = document.querySelector('.main-layout');

        // Restore state on page load
        const isExpanded = localStorage.getItem('km_library_expanded') === 'true';
        if (isExpanded) {
            librarySidebar.classList.add('expanded');
            librarySidebar.classList.remove('collapsed');
            if (mainLayout) mainLayout.classList.add('library-expanded-view');
        }

        btnExpandLibrary.addEventListener('click', (e) => {
            e.preventDefault();
            librarySidebar.classList.toggle('expanded');
            librarySidebar.classList.remove('collapsed');

            const isExpanding = librarySidebar.classList.contains('expanded');

            if (mainLayout) {
                mainLayout.classList.toggle('library-expanded-view', isExpanding);
            }

            localStorage.setItem('km_library_expanded', isExpanding);
        });
    }

    // ── Library Create Dropdown ────────────────────────────────────────────
    const btnCreate      = document.getElementById('btn-library-create');
    const createDropdown = document.getElementById('library-create-dropdown');
    const createOverlay  = document.getElementById('create-modal-overlay');
    const createInput    = document.getElementById('create-modal-input');
    const createTitle    = document.getElementById('create-modal-title');
    const createCancel   = document.getElementById('create-modal-cancel');
    const createForm     = document.getElementById('create-modal-form');
    const createEmojiWrap = document.getElementById('create-modal-emoji-wrapper');
    const createEmoji    = document.getElementById('create-modal-emoji');
    const createDesc     = document.getElementById('create-modal-desc');

    let createType = 'playlist'; // 'playlist' | 'folder'

    if (btnCreate && createDropdown) {
        btnCreate.addEventListener('click', (e) => {
            e.stopPropagation();
            createDropdown.classList.toggle('hidden');
        });
        document.addEventListener('click', (e) => {
            if (!createDropdown.contains(e.target) && e.target !== btnCreate) {
                createDropdown.classList.add('hidden');
            }
        });
    }

    // Create Playlist option
    document.getElementById('btn-create-playlist')?.addEventListener('click', () => {
        createDropdown?.classList.add('hidden');
        createType = 'playlist';
        if (createTitle) createTitle.textContent = 'Tạo Playlist mới';
        if (createInput) createInput.placeholder = 'Tên playlist...';
        if (createForm) createForm.setAttribute('action', '/playlists/create');
        
        // Show emoji & description for playlist
        if (createEmoji) createEmoji.style.display = 'block';
        if (createDesc) createDesc.style.display = 'block';
        if (createDesc) createDesc.removeAttribute('disabled');
        if (createEmoji) createEmoji.removeAttribute('disabled');
        
        createOverlay?.classList.remove('hidden');
        createInput?.focus();
    });

    // Create Folder option
    document.getElementById('btn-create-folder')?.addEventListener('click', () => {
        createDropdown?.classList.add('hidden');
        
        // Directly create folder "Thư mục mới" via API
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
        
        const params = new URLSearchParams();
        params.append('name', 'Thư mục mới');
        
        const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
        if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

        fetch('/playlists/folder/create', { method: 'POST', body: params, headers })
            .then(res => {
                if (res.redirected) {
                    if (!navigateMainContent(res.url)) {
                        window.location.href = res.url;
                    }
                } else {
                    window.location.reload();
                }
            })
            .catch(err => {
                console.error("Error creating folder:", err);
            });
    });

    // Blend option (placeholder)
    document.getElementById('btn-create-blend')?.addEventListener('click', () => {
        createDropdown?.classList.add('hidden');
        toast('Tính năng Giai điệu chung sắp ra mắt!');
    });

    // Modal cancel/confirm
    createCancel?.addEventListener('click', () => {
        createOverlay?.classList.add('hidden');
        if (createForm) createForm.reset();
    });
    createOverlay?.addEventListener('click', (e) => {
        if (e.target === createOverlay) {
            createOverlay.classList.add('hidden');
            if (createForm) createForm.reset();
        }
    });

    // Form submit will just let the browser POST normally. 
    // We don't use ajax anymore so CSRF works automatically over th:action.

    // ── Library Search ─────────────────────────────────────────────────────
    const btnLibSearch   = document.getElementById('btn-library-search');
    const libSearchInput = document.getElementById('library-search-input');
    const libraryItems   = document.getElementById('library-items-list');

    if (btnLibSearch && libSearchInput) {
        btnLibSearch.addEventListener('click', (e) => {
            e.stopPropagation();
            libSearchInput.classList.toggle('hidden');
            if (!libSearchInput.classList.contains('hidden')) {
                libSearchInput.focus();
            } else {
                libSearchInput.value = '';
                filterLibraryItems('');
            }
        });

        libSearchInput.addEventListener('input', () => {
            filterLibraryItems(libSearchInput.value.trim().toLowerCase());
        });
    }

    function filterLibraryItems(query) {
        if (!libraryItems) return;
        const items = libraryItems.querySelectorAll('.library-item');
        items.forEach(item => {
            const name = (item.dataset.name || item.querySelector('.library-item-title')?.textContent || '').toLowerCase();
            item.style.display = (!query || name.includes(query)) ? '' : 'none';
        });
    }

    // ── Library Sort ───────────────────────────────────────────────────────
    const btnSort     = document.getElementById('btn-library-sort');
    const sortDropdown = document.getElementById('library-sort-dropdown');
    const sortLabel   = document.getElementById('library-sort-label');

    if (btnSort && sortDropdown) {
        btnSort.addEventListener('click', (e) => {
            e.stopPropagation();
            sortDropdown.classList.toggle('hidden');
        });
        document.addEventListener('click', (e) => {
            if (!sortDropdown.contains(e.target) && e.target !== btnSort) {
                sortDropdown.classList.add('hidden');
            }
        });
    }

    document.querySelectorAll('.library-sort-option').forEach(opt => {
        opt.addEventListener('click', () => {
            document.querySelectorAll('.library-sort-option').forEach(o => o.classList.remove('active'));
            opt.classList.add('active');
            if (sortLabel) sortLabel.textContent = opt.textContent;
            sortDropdown?.classList.add('hidden');
            sortLibraryItems(opt.dataset.sort);
        });
    });

    function sortLibraryItems(mode) {
        if (!libraryItems) return;
        const container = libraryItems.querySelector('[sec\\:authorize="isAuthenticated()"]') || libraryItems;
        const items = Array.from(container.querySelectorAll('a.library-item'));
        if (!items.length) return;

        // Keep "Liked Songs" (first item) always on top
        const likedItem = items.find(i => i.querySelector('.liked-songs-bg'));
        const restItems = items.filter(i => !i.querySelector('.liked-songs-bg'));

        if (mode === 'alpha') {
            restItems.sort((a, b) => {
                const na = (a.dataset.name || a.querySelector('.library-item-title')?.textContent || '').toLowerCase();
                const nb = (b.dataset.name || b.querySelector('.library-item-title')?.textContent || '').toLowerCase();
                return na.localeCompare(nb, 'vi');
            });
        }
        // For 'recent' and 'added', keep DOM order (backend already sorted)

        // Re-append
        if (likedItem) container.appendChild(likedItem);
        restItems.forEach(item => container.appendChild(item));
    }

    // ── Library View Modes ─────────────────────────────────────────────────
    const viewBtns = document.querySelectorAll('.library-view-btn');
    const savedView = localStorage.getItem('km_library_view') || 'list';

    // Apply saved view on load
    if (librarySidebar) {
        applyLibraryView(savedView);
        const activeBtn = document.querySelector(`.library-view-btn[data-view="${savedView}"]`);
        if (activeBtn) {
            viewBtns.forEach(b => b.classList.remove('active'));
            activeBtn.classList.add('active');
        }
    }

    viewBtns.forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            viewBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            applyLibraryView(btn.dataset.view);
            localStorage.setItem('km_library_view', btn.dataset.view);
            const sortDropdown = document.getElementById('library-sort-dropdown');
            if (sortDropdown) sortDropdown.classList.add('hidden');
        });
    });

    function applyLibraryView(view) {
        if (!librarySidebar) return;
        librarySidebar.classList.remove('view-grid', 'view-list', 'view-compact-list', 'view-compact-grid');
        librarySidebar.classList.add('view-' + view);
    }

    // ── Notifications Panel ────────────────────────────────────────────────
    const btnNoti  = document.getElementById('btn-noti');
    const notiPanel = document.getElementById('notification-panel');
    if (btnNoti && notiPanel) {
        btnNoti.addEventListener('click', (e) => {
            e.stopPropagation();
            notiPanel.classList.toggle('hidden');
        });
        document.addEventListener('click', (e) => {
            if (!notiPanel.contains(e.target) && e.target !== btnNoti) {
                notiPanel.classList.add('hidden');
            }
        });
    }
})();

/* ============================================================ */
/* TRACK COMMENTS LOGIC                                         */
/* ============================================================ */
function initTrackComments() {
    const list = document.getElementById('comment-list');
    const input = document.getElementById('comment-input');
    const btnPost = document.getElementById('btn-post-comment');
    const tabComments = document.getElementById('comment-section-title');
    
    if (!list) return;

    const tmpEsc = (s) => String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');

    const mediaId = btnPost ? btnPost.dataset.id : null;
    if (!mediaId) {
        list.innerHTML = '<div style="color: #b3b3b3; font-size: 14px; text-align: center;">Không tìm thấy ID bài hát</div>';
        return;
    }

    function renderComment(c) {
        let dateStr = '';
        if (c.createdAt) {
            const d = new Date(c.createdAt);
            if (!isNaN(d)) dateStr = d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
        }
        const name = (c.user && c.user.displayName) ? c.user.displayName : ((c.user && c.user.username) ? c.user.username : 'Unknown');
        return `
            <div style="display: flex; gap: 12px; margin-bottom: 20px;">
                <div style="width: 40px; height: 40px; background: #333; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; color: #fff; flex-shrink: 0; font-size: 16px;">
                    ${tmpEsc(name.charAt(0).toUpperCase())}
                </div>
                <div style="flex: 1;">
                    <div style="font-size: 13px; color: #b3b3b3; margin-bottom: 6px;">
                        <span style="color: #fff; font-weight: bold; margin-right: 8px; font-size: 14px;">${tmpEsc(name)}</span>
                        ${tmpEsc(dateStr)}
                    </div>
                    <div style="font-size: 14px; color: #e5e5e5; line-height: 1.5;">${tmpEsc(c.content)}</div>
                </div>
            </div>
        `;
    }

    function loadComments() {
        if (list.innerHTML.trim() === 'Đang tải bình luận...') {
            // Keep it if it's the first time
        }

        fetch('/api/comments/' + mediaId)
            .then(res => {
                if (!res.ok) throw new Error('API Error');
                return res.json();
            })
            .then(comments => {
                if (tabComments) {
                    tabComments.textContent = 'Bình Luận (' + comments.length + ')';
                }
                if (!comments || comments.length === 0) {
                    list.innerHTML = '<div style="color: #b3b3b3; font-size: 14px; text-align: center; padding: 20px 0;">Không có bình luận. Hãy là người đầu tiên!</div>';
                    return;
                }
                list.innerHTML = comments.map(renderComment).join('');
            })
            .catch(err => {
                list.innerHTML = '<div style="color: #ed4245; font-size: 14px; text-align: center;">Lỗi tải bình luận</div>';
            });
    }

    loadComments();

    if (btnPost && !btnPost.dataset.commentInit) {
        btnPost.dataset.commentInit = "true";
        
        const postFn = () => {
            if (!input || !input.value.trim()) return;
            const text = input.value.trim();
            input.disabled = true; 
            btnPost.disabled = true;
            btnPost.style.opacity = '0.7';
            
            const fd = new URLSearchParams();
            fd.append('content', text);

            fetch('/api/comments/' + mediaId, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-XSRF-TOKEN': typeof getCsrfToken === 'function' ? getCsrfToken() : ''
                },
                body: fd
            })
            .then(res => {
                if (res.status === 401) { alert('Hãy đăng nhập để bình luận!'); throw new Error('Unauth'); }
                if (!res.ok) throw new Error('Lỗi server');
                return res.json();
            })
            .then(data => {
                input.value = '';
                input.disabled = false; 
                btnPost.disabled = false;
                btnPost.style.opacity = '1';
                loadComments();
            })
            .catch(err => {
                input.disabled = false; 
                btnPost.disabled = false;
                btnPost.style.opacity = '1';
                if (err.message !== 'Unauth') alert('Đã có lỗi xảy ra khi gửi bình luận.');
            });
        };

        btnPost.addEventListener('click', postFn);
        if (input) {
            input.addEventListener('keypress', e => {
                if (e.key === 'Enter') postFn();
            });
        }
    }
}

function initLibraryContextMenu() {
    const contextMenu = document.getElementById('km-context-menu');
    const libraryList = document.getElementById('library-items-list');
    const renameOverlay = document.getElementById('rename-modal-overlay');
    const renameInput = document.getElementById('rename-modal-input');
    const renameConfirm = document.getElementById('rename-modal-confirm');
    const renameCancel = document.getElementById('rename-modal-cancel');
    
    // Move to folder elements
    const ctxMoveFolder = document.getElementById('ctx-move-folder');
    const ctxFolderList = document.getElementById('ctx-folder-list');
    const ctxFolderSearch = document.getElementById('ctx-folder-search');
    const ctxRemoveFromFolder = document.getElementById('ctx-remove-from-folder');

    let currentItem = null; // { id, name, type }
    let cachedFolders = [];

    if (!contextMenu || !libraryList) return;

    // Load folders for submenu
    async function loadFoldersForSubmenu() {
        try {
            const res = await fetch('/api/my-folders');
            if (res.ok) {
                cachedFolders = await res.json();
                renderFolderSubmenu('');
            }
        } catch (e) { console.error('Failed to load folders for submenu', e); }
    }

    function renderFolderSubmenu(filterText) {
        if (!ctxFolderList) return;
        ctxFolderList.innerHTML = '';
        const search = filterText.toLowerCase();
        
        cachedFolders.filter(f => f.name.toLowerCase().includes(search)).forEach(folder => {
            const btn = document.createElement('button');
            btn.className = 'km-context-item';
            btn.style.width = '100%';
            btn.style.fontSize = '13px';
            btn.style.padding = '8px 12px';
            btn.innerHTML = `<span>${folder.name}</span>`;
            
            btn.addEventListener('click', (e) => {
                e.stopPropagation(); // Prevent closing parent immediately, let action handle it
                movePlaylistToFolder(folder.id);
            });
            ctxFolderList.appendChild(btn);
        });
        
        if (ctxFolderList.children.length === 0) {
            ctxFolderList.innerHTML = '<div style="padding:8px 12px; color:#b3b3b3; font-size:12px;">Không tìm thấy</div>';
        }
    }
    
    function movePlaylistToFolder(folderId) {
        if (!currentItem || currentItem.type !== 'playlist') return;
        
        const fd = new URLSearchParams();
        fd.append('folderId', folderId || '');
        
        fetch(`/playlists/${currentItem.id}/move`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-XSRF-TOKEN': getCsrfToken()
            },
            body: fd
        })
        .then(res => {
            if (res.ok) window.location.reload();
            else alert('Lỗi di chuyển danh sách phát');
        })
        .catch(err => console.error(err))
        .finally(() => {
            contextMenu.classList.add('hidden');
        });
    }

    // Handle Right Click on Library Items
    libraryList.addEventListener('contextmenu', (e) => {
        const item = e.target.closest('.library-item');
        if (item && item.dataset.id) {
            e.preventDefault();
            currentItem = {
                id: item.dataset.id,
                name: item.dataset.name,
                type: item.classList.contains('is-folder') ? 'folder' : 'playlist'
            };

            // Toggle Move To Folder option visibility
            if (ctxMoveFolder) {
                if (currentItem.type === 'folder') {
                    ctxMoveFolder.style.display = 'none';
                } else {
                    ctxMoveFolder.style.display = 'flex';
                    loadFoldersForSubmenu(); // Load sub-folders whenever opening on a playlist
                }
            }

            contextMenu.style.top = `${e.clientY}px`;
            contextMenu.style.left = `${e.clientX}px`;
            contextMenu.classList.remove('hidden');
        }
    });

    // Hide context menu when clicking outside
    document.addEventListener('click', (e) => {
        if (!contextMenu.classList.contains('hidden') && !contextMenu.contains(e.target)) {
            contextMenu.classList.add('hidden');
        }
    });

    // Submenu filtering
    if (ctxFolderSearch) {
        ctxFolderSearch.addEventListener('input', (e) => {
            renderFolderSubmenu(e.target.value);
        });
        
        // Prevent closing context menu when typing in search
        ctxFolderSearch.addEventListener('click', (e) => e.stopPropagation());
    }
    
    // Remove from folder action
    if (ctxRemoveFromFolder) {
        ctxRemoveFromFolder.addEventListener('click', (e) => {
            e.stopPropagation();
            movePlaylistToFolder(''); // Empty folder ID removes it
        });
    }

    // Hover logic for Submenu
    if (ctxMoveFolder) {
        const submenu = document.getElementById('ctx-move-submenu');
        ctxMoveFolder.addEventListener('mouseenter', () => {
             if (submenu) submenu.classList.remove('hidden');
        });
        ctxMoveFolder.addEventListener('mouseleave', () => {
             if (submenu) submenu.classList.add('hidden');
        });
        if (submenu) {
             submenu.addEventListener('mouseenter', () => submenu.classList.remove('hidden'));
             submenu.addEventListener('mouseleave', () => submenu.classList.add('hidden'));
        }
    }

    // Rename Action
    document.getElementById('ctx-rename')?.addEventListener('click', () => {
        contextMenu.classList.add('hidden');
        if (!currentItem) return;

        renameInput.value = currentItem.name;
        renameOverlay.classList.remove('hidden');
        renameInput.focus();
        renameInput.select();
    });

    renameConfirm?.addEventListener('click', async () => {
        const newName = renameInput.value.trim();
        if (!newName || !currentItem) return;

        renameConfirm.disabled = true;
        try {
            const fd = new URLSearchParams();
            fd.append('name', newName);
            const res = await fetch(`/playlists/${currentItem.id}/rename`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-XSRF-TOKEN': getCsrfToken()
                },
                body: fd
            });
            if (res.ok) {
                window.location.reload();
            } else {
                alert('Lỗi đổi tên');
            }
        } catch (err) {
            console.error(err);
        }
        renameConfirm.disabled = false;
        renameOverlay.classList.add('hidden');
    });

    renameCancel?.addEventListener('click', () => renameOverlay.classList.add('hidden'));
    
    // Also close on Escape or Backdrop click
    renameOverlay.addEventListener('click', (e) => { if (e.target === renameOverlay) renameOverlay.classList.add('hidden'); });

    // Delete Action
    document.getElementById('ctx-delete')?.addEventListener('click', () => {
        contextMenu.classList.add('hidden');
        if (!currentItem) return;

        if (confirm(`Xoá ${currentItem.type === 'folder' ? 'thư mục' : 'danh sách phát'} "${currentItem.name}"?`)) {
            fetch(`/playlists/${currentItem.id}/delete`, {
                method: 'POST',
                headers: { 'X-XSRF-TOKEN': getCsrfToken() }
            }).then(() => window.location.reload());
        }
    });

    // Create Actions
    document.getElementById('ctx-create-playlist')?.addEventListener('click', () => {
        contextMenu.classList.add('hidden');
        document.getElementById('btn-create-playlist')?.click();
    });
    document.getElementById('ctx-create-folder')?.addEventListener('click', () => {
        contextMenu.classList.add('hidden');
        document.getElementById('btn-create-folder')?.click();
    });
}

document.addEventListener('DOMContentLoaded', () => {
    initTrackComments();
    initLibraryContextMenu();
});
document.body.addEventListener('htmx:afterOnLoad', () => {
    initTrackComments();
    initLibraryContextMenu();
});
