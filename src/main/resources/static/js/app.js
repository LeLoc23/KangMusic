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
    document.body.addEventListener('htmx:afterOnLoad', updateActiveNav);
    updateActiveNav();

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

    // Right Sidebar Elements
    const rightSidebar      = document.getElementById('right-sidebar');
    const queueListSide     = document.getElementById('queue-list-sidebar');
    const lyricsContentSide = document.getElementById('lyrics-content-sidebar');

    if (!audio) return; // trang không có player

    // ── State ──────────────────────────────────────────────────────────────
    let queue       = loadQueue();
    let queueIndex  = loadQueueIndex();
    let shuffle     = false;
    let repeatMode  = 'none';        // 'none' | 'one' | 'all'
    let isDragging  = false;
    let currentTrack = null;

    const PLAY_SVG  = `<svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28"><path d="M8 5v14l11-7z"/></svg>`;
    const PAUSE_SVG = `<svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>`;

    // ── localStorage helpers ───────────────────────────────────────────────
    function saveQueue() {
        try {
            localStorage.setItem('km_queue', JSON.stringify(queue));
            localStorage.setItem('km_queue_index', queueIndex);
        } catch (e) { /* quota exceeded — ignore */ }
    }
    function loadQueue() {
        try { return JSON.parse(localStorage.getItem('km_queue') || '[]'); }
        catch { return []; }
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
            artworkIcon.textContent = track.type === 'VIDEO' ? '🎬' : '🎵';
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
            rsIcon.textContent = track.type === 'VIDEO' ? '🎬' : '🎵';
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
        const playBtn = e.target.closest('.media-play-btn');
        if (!playBtn) return;

        const track = trackFromBtn(playBtn);

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
            if (window.htmx) {
                window.htmx.ajax('GET', href, { target: '#main-content', pushUrl: true });
            } else {
                window.location.href = href;
            }
        }
    });

    // Nút "Thêm vào hàng chờ"
    document.body.addEventListener('click', e => {
        const qBtn = e.target.closest('[data-action="queue"]');
        if (!qBtn) return;
        e.stopPropagation();
        const card = qBtn.closest('.media-card');
        const playBtn = card?.querySelector('.media-play-btn');
        if (playBtn) addToQueue(trackFromBtn(playBtn));
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

    btnRepeat.addEventListener('click', () => {
        const modes = ['none', 'one', 'all'];
        repeatMode = modes[(modes.indexOf(repeatMode) + 1) % modes.length];
        const labels = { none: 'Tắt lặp', one: 'Lặp 1 bài', all: 'Lặp playlist' };
        btnRepeat.classList.toggle('btn-active', repeatMode !== 'none');
        btnRepeat.dataset.mode = repeatMode;
        showToast(labels[repeatMode]);
        btnRepeat.querySelector('svg').style.opacity = repeatMode === 'none' ? '0.6' : '1';
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

    // Player bar lyrics button → goes to lyrics view
    btnLyrics?.addEventListener('click', (e) => {
        e.stopPropagation();
        rightSidebar?.classList.remove('hidden', 'collapsed');
        setSidebarView('lyrics');
    });

    // Player bar Now Playing (Đang phát) button
    const btnNowPlaying = document.getElementById('btn-now-playing');
    btnNowPlaying?.addEventListener('click', (e) => {
        e.stopPropagation();
        rightSidebar?.classList.remove('hidden', 'collapsed');
        setSidebarView('playing');
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
    });

    // ── Audio events ───────────────────────────────────────────────────────
    audio.addEventListener('play',  () => { btnPlay.innerHTML = PAUSE_SVG; });
    audio.addEventListener('pause', () => { btnPlay.innerHTML = PLAY_SVG; });

    audio.addEventListener('timeupdate', () => {
        if (isDragging || !isFinite(audio.duration)) return;
        const pct = (audio.currentTime / audio.duration) * 100;
        seekBar.value = pct;
        currentTimeEl.textContent = fmt(audio.currentTime);
        totalTimeEl.textContent   = fmt(audio.duration);
        
        // Save current time to restore if page reloads
        try {
            localStorage.setItem('km_currentTime', audio.currentTime);
            localStorage.setItem('km_currentQueueIndex', queueIndex);
        } catch (e) {}
    });

    audio.addEventListener('loadedmetadata', () => {
        totalTimeEl.textContent = fmt(audio.duration);
        seekBar.value = 0;
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
        const emoji = track.type === 'VIDEO' ? '🎬' : '🎵';
        const thumbHTML = track.poster 
            ? `<img src="/stream/${track.poster}" style="width:100%; height:100%; object-fit:cover; border-radius:4px; display:block;">` 
            : `<span>${emoji}</span>`;
        item.innerHTML = `
            <div class="queue-item-thumb" style="display:flex; justify-content:center; align-items:center; overflow:hidden;">${thumbHTML}</div>
            <div class="queue-item-info">
                <div class="queue-item-title">${escHtml(track.title)}</div>
                <div class="queue-item-artist">${escHtml(track.artist)}</div>
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

        if (!nowSection || !nextSection || !queueList || !emptyState) return;

        if (queue.length === 0) {
            nowSection.style.display  = 'none';
            nextSection.style.display = 'none';
            emptyState.style.display  = 'block';
            return;
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
    }

    // ── Lyrics update ──────────────────────────────────────────────────────
    function updateLyrics(track) {
        const el = document.getElementById('lyrics-content-sidebar');
        if (!el) return;
        if (track.lyrics && track.lyrics.trim()) {
            el.textContent = track.lyrics;
        } else {
            el.innerHTML = '<div style="color:#b3b3b3; text-align:center; padding:40px 0; font-size:14px;">Không có lời bài hát</div>';
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
                artworkIcon.textContent = track.type === 'VIDEO' ? '🎬' : '🎵';
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

    // ── Scroll listeners ───────────────────────────────────────────────────
    const contentArea   = document.querySelector('.content-area');
    const contentHeader = document.querySelector('.content-header');
    if (contentArea && contentHeader) {
        contentArea.addEventListener('scroll', () => {
            contentHeader.classList.toggle('header-scrolled', contentArea.scrollTop > 20);
        }, { passive: true });
    }

    // ── Like button trong player bar ──────────────────────────────────────
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
                    window.location.href = res.url;
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
