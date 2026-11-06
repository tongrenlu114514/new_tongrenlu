/* ==========================================================================
   东方同人录 — 首页 v3.0 "INDEX"
   Vanilla ES2019+. No dependencies beyond folia-player.js (buildFoliaAlbumUrl).
   --------------------------------------------------------------------------
   01 helpers          02 wall (hero mosaic)    03 entrance + reveals
   04 data + counts    05 latest rail           06 creators index
   07 events           08 roam strips
   09 nav / menu       10 random access          11 boot
   ========================================================================== */
(function () {
  'use strict';

  /* 01 ── helpers -------------------------------------------------------- */
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const finePointer = window.matchMedia('(hover: hover) and (pointer: fine)').matches;
  const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
  const ESC = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));
  const fmt = (n) => Number(n || 0).toLocaleString('zh-CN');

  async function fetchJSON(url, timeout) {
    const ctrl = new AbortController();
    const t = setTimeout(() => ctrl.abort(), timeout || 9000);
    try {
      const res = await fetch(url, { signal: ctrl.signal, headers: { Accept: 'application/json' } });
      if (!res.ok) throw new Error(res.status);
      return await res.json();
    } finally {
      clearTimeout(t);
    }
  }

  /** 网易云封面支持 ?param=WyH 裁剪，避免拉原图 */
  function cover(url, w) {
    if (!url) return '';
    if (/music\.126\.net|127\.net/.test(url)) return url.split('?')[0] + '?param=' + w + 'y' + w;
    return url;
  }

  function folia(id) {
    return typeof buildFoliaAlbumUrl === 'function'
      ? buildFoliaAlbumUrl(id)
      : 'http://121.37.183.44/folia/?album=' + encodeURIComponent(id);
  }

  function openFolia(id) {
    if (id == null) { location.href = 'album.html'; return; }
    window.open(folia(id), '_blank', 'noopener');
  }

  const fmtDate = (raw) => {
    const d = raw ? new Date(raw) : null;
    if (!d || isNaN(d.getTime())) return '';
    return d.getFullYear() + '.' + String(d.getMonth() + 1).padStart(2, '0');
  };

  /* shared state */
  const state = {
    albums: [],        // pool of {id,title,artist,quote,cover}
    latest: [],        // rail albums
    random: [],        // small buffer for instant random access
    counts: { albums: 0, artists: 0, events: 0 },
  };

  /* 02 ── the living wall ------------------------------------------------ */
  const wall = $('#wall');
  const wallState = { tiles: [], cols: 0, rows: 0, pool: [], idx: 0, timer: null, hovered: false, visible: true };

  function wallGrid() {
    const cs = getComputedStyle(document.documentElement);
    return {
      cols: parseInt(cs.getPropertyValue('--wall-cols')) || 6,
      rows: parseInt(cs.getPropertyValue('--wall-rows')) || 4,
    };
  }

  function buildWall() {
    const { cols, rows } = wallGrid();
    const total = cols * rows;
    if (!wall || (total === wallState.cols * wallState.rows && wallState.tiles.length)) return;

    wall.style.setProperty('--wall-cols', cols);
    wall.style.setProperty('--wall-rows', rows);
    wallState.cols = cols;
    wallState.rows = rows;
    wall.innerHTML = '';
    wallState.tiles = [];

    for (let i = 0; i < total; i++) {
      const tile = document.createElement('div');
      tile.className = 'tile';
      tile.style.setProperty('--d', (i * 38) + 'ms');
      const a = document.createElement('span');
      const b = document.createElement('span');
      const tag = document.createElement('span');
      a.className = 'tile__layer';
      b.className = 'tile__layer';
      tag.className = 'tile__tag';
      tile.append(a, b, tag);
      wall.appendChild(tile);
      wallState.tiles.push({ el: tile, layers: [a, b], tag, front: 0, id: null });
    }
  }

  function paintTile(tile, album, instant) {
    if (!album) return;
    const back = tile.layers[1 - tile.front];
    const front = tile.layers[tile.front];
    const url = cover(album.coverUrl || album.cover, 240);
    if (!url) return;
    if (instant) {
      front.style.backgroundImage = 'url("' + url + '")';
      front.classList.add('is-on');
      back.classList.remove('is-on');
    } else {
      const img = new Image();
      img.onload = () => {
        back.style.backgroundImage = 'url("' + url + '")';
        back.classList.add('is-on');
        front.classList.remove('is-on');
        tile.front = 1 - tile.front;
      };
      img.src = url;
    }
    tile.id = album.id;
    tile.el.dataset.id = album.id;
    if (tile.tag) tile.tag.textContent = album.code || album.title || '';
  }

  function nextFromPool() {
    if (!wallState.pool.length) return null;
    const a = wallState.pool[wallState.idx % wallState.pool.length];
    wallState.idx++;
    return a;
  }

  function fillWall(pool, instant) {
    wallState.pool = pool || [];
    wallState.tiles.forEach((tile, i) => {
      paintTile(tile, wallState.pool[i % wallState.pool.length] || null, instant);
    });
  }

  function startWallShuffle() {
    stopWallShuffle();
    if (reduced) return;
    wallState.timer = setInterval(() => {
      if (!wallState.visible || document.hidden || wallState.hovered) return;
      const free = wallState.tiles.filter((t) => !t.el.matches(':hover'));
      if (!free.length) return;
      const tile = free[(Math.random() * free.length) | 0];
      paintTile(tile, nextFromPool(), false);
    }, 1500);
  }
  function stopWallShuffle() { if (wallState.timer) clearInterval(wallState.timer); wallState.timer = null; }

  if (wall) {
    buildWall();

    let resizeT;
    window.addEventListener('resize', () => {
      clearTimeout(resizeT);
      resizeT = setTimeout(() => {
        const g = wallGrid();
        if (g.cols !== wallState.cols || g.rows !== wallState.rows) {
          wallState.cols = 0; // force rebuild
          buildWall();
          restoreWallPool();
        }
      }, 220);
    });

    // the wall holds still while the pointer is on it (pointerover bubbles,
    // pointerleave fires on the element the pointer exits — mouseenter would not
    // reach an ancestor listener here)
    wall.addEventListener('pointerover', (e) => {
      if (e.pointerType !== 'touch') wallState.hovered = true;
    });
    wall.addEventListener('pointerleave', () => { wallState.hovered = false; });

    wall.addEventListener('click', (e) => {
      const tile = e.target.closest('.tile');
      if (tile && tile.dataset.id) openFolia(tile.dataset.id);
    });

    if ('IntersectionObserver' in window) {
      new IntersectionObserver((entries) => {
        wallState.visible = entries[0].isIntersecting;
      }, { threshold: 0 }).observe(wall);
    }
  }

  function restoreWallPool() {
    if (wallState.pool.length) fillWall(wallState.pool, true);
  }

  /* 03 ── entrance + reveals --------------------------------------------- */
  function enter() {
    document.body.classList.add('is-ready');
  }

  /* Chromium reports an empty intersection for elements clipped to zero height
     (clip-path), so a scroll-driven sweep is used instead of IntersectionObserver:
     deterministic, one code path, and it cannot leave content invisible. */
  function sweepReveals() {
    const vh = window.innerHeight;
    $$('[data-reveal]:not(.is-in), .event:not(.is-in)').forEach((el) => {
      const r = el.getBoundingClientRect();
      if (r.top < vh * 0.94 && r.bottom > 0) el.classList.add('is-in');
    });
  }

  /* 04 ── data + count-up ------------------------------------------------ */
  function countUp(el, target, duration) {
    if (!el) return;
    if (reduced) { el.textContent = fmt(target); return; }
    const start = performance.now();
    const dur = duration || 1400;
    const from = 0;
    el.dataset.count = String(target);
    function step(now) {
      const p = clamp((now - start) / dur, 0, 1);
      const eased = 1 - Math.pow(1 - p, 4);
      el.textContent = fmt(Math.round(from + (target - from) * eased));
      if (p < 1) requestAnimationFrame(step);
      else el.textContent = fmt(target);
    }
    requestAnimationFrame(step);
  }

  function writeCounts() {
    const { albums, artists, events } = state.counts;

    const navA = $('#navAlbums');
    if (navA && albums) navA.textContent = fmt(albums);
    const menuA = $('#menuAlbums');
    if (menuA && albums) menuA.textContent = fmt(albums) + ' 张';
    const menuAr = $('#menuArtists');
    if (menuAr && artists) menuAr.textContent = fmt(artists) + ' 位';
    const menuE = $('#menuEvents');
    if (menuE && events) menuE.textContent = fmt(events) + ' 场';

    const footA = $('#footAlbums');
    if (footA && albums) footA.textContent = fmt(albums);
    const footAr = $('#footArtists');
    if (footAr && artists) footAr.textContent = fmt(artists);
    const footE = $('#footEvents');
    if (footE && events) footE.textContent = fmt(events);

    const artistTotal = $('#artistTotal');
    if (artistTotal && artists) artistTotal.textContent = fmt(artists);

    [
      ['#statAlbums', albums],
      ['#statArtists', artists],
      ['#statEvents', events],
    ].forEach(([sel, v]) => { if (v) countUp($(sel), v, 1500); });

    $$('[data-count-target]').forEach((el) => {
      const key = el.dataset.countTarget;
      if (state.counts[key]) countUp(el, state.counts[key], 1700);
    });
  }

  async function loadCounts() {
    const jobs = [
      fetchJSON('api/music/album-stats').then((r) => {
        const d = (r && r.data) || {};
        state.counts.albums = Number(d.published || d.total || 0);
      }),
      fetchJSON('api/music/artists?limit=1&page=1').then((r) => {
        state.counts.artists = Number((r && r.total) || 0);
      }),
      fetchJSON('api/events/count').then((r) => {
        state.counts.events = Number((r && r.count) || 0);
      }),
    ];
    await Promise.allSettled(jobs);
    writeCounts();
  }

  /* album pool for the wall / strips / random buffer */
  async function loadPool() {
    const { cols, rows } = wallGrid();
    const need = Math.max(48, cols * rows * 2);
    try {
      const list = await fetchJSON('api/music/random-albums?count=' + need);
      const pool = (list || [])
        .filter((a) => a && a.cloudMusicPicUrl)
        .map((a) => ({
          id: a.id,
          title: a.title || '',
          artist: a.artist || '',
          code: a.code || '',
          cover: a.cloudMusicPicUrl,
        }));
      if (!pool.length) throw new Error('empty');
      state.albums = pool;
      state.random = pool.slice(0, 6).map((a) => a.id);
      fillWall(pool, true);
      requestAnimationFrame(() => {
        wallState.tiles.forEach((t) => t.el.classList.add('is-ready'));
      });
      startWallShuffle();
      wallState.poolRef = pool;
      scheduleStrips();
    } catch (err) {
      // fail quiet: geometric placeholder tiles keep the hero composed
      wallState.tiles.forEach((t) => {
        t.layers[0].classList.add('is-on');
        t.el.classList.add('is-ready');
      });
    }
  }

  /* 05 ── latest rail ---------------------------------------------------- */
  const rail = $('#rail');
  const latestTrack = $('#latestTrack');

  function sleeveHTML(a, i) {
    return '<a class="sleeve" href="' + folia(a.id) + '" target="_blank" rel="noopener" style="--d:' + (i * 60) + 'ms">' +
      '<div class="sleeve__art">' +
        '<img src="' + ESC(cover(a.cloudMusicPicUrl, 340)) + '" alt="' + ESC(a.title || '专辑封面') + '" loading="lazy" decoding="async" onerror="this.style.opacity=0">' +
        (a.code ? '<span class="sleeve__code">' + ESC(a.code) + '</span>' : '') +
        '<span class="sleeve__play" aria-hidden="true">' +
          '<svg width="11" height="11" viewBox="0 0 12 12" fill="currentColor"><path d="M3 1.5l7 4.5-7 4.5z"/></svg>' +
        '</span>' +
      '</div>' +
      '<div class="sleeve__meta">' +
        '<h3 class="sleeve__title">' + ESC(a.title || '未命名专辑') + '</h3>' +
        '<p class="sleeve__artist">' + ESC(a.artist || '—') + '</p>' +
      '</div>' +
    '</a>';
  }

  async function loadLatest() {
    try {
      const data = await fetchJSON('api/music/search?keyword=&pageNumber=1&pageSize=12&orderBy=publishDate');
      const list = (data && (data.records || data)) || [];
      const albums = list.filter((a) => a && a.id);
      if (!albums.length) throw new Error('empty');
      state.latest = albums;
      latestTrack.innerHTML = albums.map(sleeveHTML).join('');
      // fades in as the cards arrive
      $$('.sleeve', latestTrack).forEach((el, i) => {
        if (reduced) return;
        el.style.opacity = '0';
        el.style.transform = 'translateY(14px)';
        el.style.transition = 'opacity .8s cubic-bezier(.2,.85,.2,1) ' + (i * 55) + 'ms, transform .8s cubic-bezier(.2,.85,.2,1) ' + (i * 55) + 'ms';
        requestAnimationFrame(() => { el.style.opacity = '1'; el.style.transform = 'none'; });
      });

      const d = fmtDate(albums[0].publishDate);
      if (d) $('#updatedAt').textContent = '更新至 ' + d;
      updateRailUI();
    } catch (err) {
      latestTrack.innerHTML = '<p class="sec-lede" style="padding:1rem 0">档案暂时没有响应，请稍后再来。</p>';
    }
  }

  function updateRailUI() {
    if (!rail) return;
    const max = rail.scrollWidth - rail.clientWidth;
    const p = max > 8 ? rail.scrollLeft / max : 1;
    const bar = $('#railProgress');
    if (bar) bar.style.transform = 'scaleX(' + clamp(p, 0.02, 1) + ')';
    const prev = $('#railPrev');
    const next = $('#railNext');
    if (prev) prev.disabled = rail.scrollLeft < 8;
    if (next) next.disabled = rail.scrollLeft >= max - 8;
  }

  if (rail) {
    rail.addEventListener('scroll', updateRailUI, { passive: true });

    // drag to scroll (pointer: fine only)
    if (finePointer) {
      let down = false, startX = 0, startLeft = 0, moved = 0;
      rail.addEventListener('pointerdown', (e) => {
        if (e.pointerType === 'touch' || e.button !== 0) return;
        down = true; moved = 0;
        startX = e.clientX; startLeft = rail.scrollLeft;
        rail.classList.add('is-dragging');
      });
      rail.addEventListener('pointermove', (e) => {
        if (!down) return;
        const dx = e.clientX - startX;
        moved = Math.max(moved, Math.abs(dx));
        if (moved > 3) e.preventDefault();
        rail.scrollLeft = startLeft - dx;
      });
      const end = () => {
        if (!down) return;
        down = false;
        rail.classList.remove('is-dragging');
        if (moved > 40) rail.dataset.justDragged = '1';
        setTimeout(() => { delete rail.dataset.justDragged; }, 60);
      };
      rail.addEventListener('pointerup', end);
      rail.addEventListener('pointercancel', end);
      rail.addEventListener('pointerleave', end);
      rail.addEventListener('click', (e) => {
        if (rail.dataset.justDragged) { e.preventDefault(); e.stopPropagation(); }
      }, true);
    }

    const step = () => {
      const card = $('.sleeve', rail);
      return card ? card.getBoundingClientRect().width + 18 : 300;
    };
    const prev = $('#railPrev');
    const next = $('#railNext');
    if (prev) prev.addEventListener('click', () => rail.scrollBy({ left: -step() * 2, behavior: 'smooth' }));
    if (next) next.addEventListener('click', () => rail.scrollBy({ left: step() * 2, behavior: 'smooth' }));

    rail.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowRight') { e.preventDefault(); rail.scrollBy({ left: step(), behavior: 'smooth' }); }
      if (e.key === 'ArrowLeft') { e.preventDefault(); rail.scrollBy({ left: -step(), behavior: 'smooth' }); }
    });

    window.addEventListener('resize', () => setTimeout(updateRailUI, 200));
  }

  /* 06 ── creators index ------------------------------------------------- */
  const peek = $('#peek');
  const peekImg = $('#peekImg');
  const peekState = { x: 0, y: 0, tx: 0, ty: 0, on: false };

  function bindPeek(rows) {
    if (!finePointer || reduced || !peek) return;
    rows.forEach((row) => {
      row.addEventListener('mouseenter', () => {
        const url = row.dataset.img;
        if (!url) return;
        peekImg.style.backgroundImage = 'url("' + url + '")';
        peek.classList.add('is-on');
        peekState.on = true;
        const r = row.getBoundingClientRect();
        peekState.x = peekState.tx = r.left + r.width * 0.5;
        peekState.y = peekState.ty = r.top + r.height * 0.5;
      });
      row.addEventListener('mouseleave', () => {
        peek.classList.remove('is-on');
        peekState.on = false;
      });
    });
  }

  async function loadArtists() {
    const list = $('#artistIndex');
    if (!list || list.dataset.loaded) return;
    list.dataset.loaded = '1';
    // skeletons first, so the column never looks empty
    list.innerHTML = Array.from({ length: 6 }).map(() =>
      '<li class="index__row" aria-hidden="true">' +
        '<span class="index__no sk" style="height:9px;width:16px"></span>' +
        '<span class="index__name sk" style="height:16px;width:52%"></span>' +
        '<span class="index__count sk" style="height:9px;width:44px"></span>' +
        '<span class="index__arw"></span>' +
      '</li>').join('');

    try {
      const data = await fetchJSON('api/music/artists?limit=8&page=1');
      const records = (data && data.records) || [];
      if (!records.length) throw new Error('empty');
      list.innerHTML = records.map((a, i) => {
        const name = a.name || '未知创作者';
        const count = Number(a.album_count || 0);
        return '<li>' +
          '<a class="index__row" href="album.html?keyword=' + encodeURIComponent(name) + '"' +
             ' data-img="' + ESC(cover(a.cloud_music_pic_url, 300)) + '"' +
             ' style="--d:' + (i * 45) + 'ms">' +
            '<span class="index__no">' + String(i + 1).padStart(2, '0') + '</span>' +
            '<span class="index__name">' + ESC(name) + '</span>' +
            '<span class="index__count">' + fmt(count) + ' 张</span>' +
            '<span class="index__arw" aria-hidden="true">' +
              '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M7 17L17 7M9 7h8v8"/></svg>' +
            '</span>' +
          '</a>' +
        '</li>';
      }).join('');
      bindPeek($$('.index__row', list));
    } catch (err) {
      list.innerHTML = '<li class="index__row" style="grid-template-columns:1fr">' +
        '<span class="index__name" style="font-size:.95rem;font-weight:400;color:var(--ink-a)">创作者索引暂时无法加载</span></li>';
    }
  }

  /* 07 ── events --------------------------------------------------------- */
  async function loadEvents() {
    const list = $('#eventList');
    if (!list || list.dataset.loaded) return;
    list.dataset.loaded = '1';
    list.innerHTML = Array.from({ length: 6 }).map(() =>
      '<li class="event" aria-hidden="true">' +
        '<span class="event__no sk" style="height:9px;width:16px"></span>' +
        '<span class="event__name sk" style="height:14px;width:58%"></span>' +
        '<span class="event__count sk" style="height:14px;width:34px;margin-left:auto"></span>' +
        '<span class="event__bar"></span>' +
      '</li>').join('');

    try {
      const data = await fetchJSON('api/events?limit=9&page=1&orderBy=album_count');
      const records = (data && data.records) || [];
      if (!records.length) throw new Error('empty');
      const max = Math.max.apply(null, records.map((e) => Number(e.album_count || 0))) || 1;

      list.innerHTML = records.map((e, i) => {
        const count = Number(e.album_count || 0);
        const v = clamp(count / max, 0.06, 1).toFixed(3);
        return '<li class="event" style="--v:' + v + ';--d:' + (i * 60) + 'ms">' +
          '<span class="event__no">' + String(i + 1).padStart(2, '0') + '</span>' +
          '<span class="event__name">' + ESC(e.tag || '—') + '</span>' +
          '<span class="event__count">' + fmt(count) + '</span>' +
          '<span class="event__bar" aria-hidden="true"><i></i></span>' +
        '</li>';
      }).join('');

      // bars grow on reveal (viewport sweep in the main loop)
    } catch (err) {
      list.innerHTML = '<li class="event" style="grid-template-columns:1fr">' +
        '<span class="event__name" style="font-size:.95rem;font-weight:400;color:var(--ink-a)">展会索引暂时无法加载</span></li>';
    }
  }

  /* 08 ── roam strips ---------------------------------------------------- */
  function scheduleStrips() {
    const roam = document.querySelector('.roam');
    if (!roam) return;
    const run = () => {
      if (stripsDone) return;
      stripsDone = true;
      buildStrips(wallState.poolRef || []);
    };
    if ('IntersectionObserver' in window) {
      const io = new IntersectionObserver((entries) => {
        entries.forEach((en) => {
          if (!en.isIntersecting) return;
          io.unobserve(en.target);
          run();
        });
      }, { rootMargin: '1200px 0px' });
      io.observe(roam);
    }
    // never leave the band empty
    setTimeout(run, 4200);
  }

  let stripsDone = false;

  function buildStrips(pool) {
    const a = $('#stripA');
    const b = $('#stripB');
    if (!a || !b || !pool.length) return;
    const make = (seed, n) => {
      let out = '';
      for (let i = 0; i < n; i++) {
        const item = pool[(seed + i * 3) % pool.length];
        out += '<span class="strip__img" style="background-image:url(\'' + cover(item.cover, 180) + '\')"></span>';
      }
      return out + out; // duplicate for a seamless loop
    };
    a.innerHTML = make(0, 9);
    b.innerHTML = make(5, 9);
  }

  /* 09 ── nav, progress, menu ------------------------------------------- */
  const nav = $('#nav');
  const progressBar = $('#progressBar');
  const burger = $('#burger');
  const menu = $('#menu');
  let menuOpen = false;
  let lastFocus = null;
  let lockY = null;

  function setMenu(open) {
    menuOpen = open;
    if (burger) burger.setAttribute('aria-expanded', String(open));
    if (burger) burger.setAttribute('aria-label', open ? '关闭菜单' : '打开菜单');
    if (menu) {
      menu.classList.toggle('is-open', open);
      menu.setAttribute('aria-hidden', String(!open));
      if ('inert' in menu) menu.inert = !open;
    }
    document.body.classList.toggle('menu-open', open);
    // lock the page under the overlay and give the position back on close
    if (open) {
      lockY = window.scrollY;
      document.documentElement.style.setProperty('--lock-y', -lockY + 'px');
      document.documentElement.classList.add('is-locked');
      window.scrollTo(0, 0);
    } else if (lockY !== null) {
      document.documentElement.classList.remove('is-locked');
      window.scrollTo(0, lockY);
      lockY = null;
    }
    if (open) {
      lastFocus = document.activeElement;
      const first = $('.menu__item', menu);
      if (first) setTimeout(() => first.focus({ preventScroll: true }), 320);
    } else if (lastFocus && lastFocus.focus) {
      lastFocus.focus({ preventScroll: true });
    }
    if (nav) nav.classList.toggle('is-stuck', open ? false : window.scrollY > 40);
  }

  if (menu && 'inert' in menu) menu.inert = true; // closed menu is not tabbable
  if (burger) burger.addEventListener('click', () => setMenu(!menuOpen));
  if (menu) {
    menu.addEventListener('click', (e) => { if (e.target.closest('.menu__item')) setMenu(false); });
  }
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && menuOpen) setMenu(false);
  });
  const mq = window.matchMedia('(max-width: 900px)');
  const onMq = () => { if (!mq.matches && menuOpen) setMenu(false); };
  if (mq.addEventListener) mq.addEventListener('change', onMq);

  /* 10 ── random access -------------------------------------------------- */
  async function refillRandom() {
    try {
      const list = await fetchJSON('api/music/random-albums?count=6');
      const ids = (list || []).map((a) => a && a.id).filter(Boolean);
      if (ids.length) state.random = ids;
    } catch (e) { /* keep whatever we have */ }
  }

  function randomize() {
    let id = state.random.shift();
    if (id == null && state.latest.length) {
      id = state.latest[(Math.random() * state.latest.length) | 0].id;
    }
    if (id == null && state.albums.length) {
      id = state.albums[(Math.random() * state.albums.length) | 0].id;
    }
    openFolia(id);
    if (state.random.length < 2) refillRandom();
  }

  ['#shuffleBtn', '#heroRandom', '#randomBtn'].forEach((sel) => {
    const el = $(sel);
    if (el) el.addEventListener('click', randomize);
  });

  /* 11 ── boot + single rAF loop ---------------------------------------- */
  function boot() {
    loadCounts();
    loadPool();
    loadLatest();

    // below-the-fold data waits until it is nearly in view
    if ('IntersectionObserver' in window) {
      const lazy = new IntersectionObserver((entries) => {
        entries.forEach((en) => {
          if (!en.isIntersecting) return;
          lazy.unobserve(en.target);
          if (en.target.id === 'creators') loadArtists();
          if (en.target.id === 'events') loadEvents();
        });
      }, { rootMargin: '500px 0px' });
      const c = $('#creators'); if (c) lazy.observe(c);
      const e = $('#events'); if (e) lazy.observe(e);
    } else {
      loadArtists(); loadEvents();
    }
    // belt and braces: if the observers never fire (embedded frames, exotic
    // browsers) the two index lists still arrive shortly after first paint.
    setTimeout(() => {
      if (!$('#artistIndex').dataset.loaded) loadArtists();
      if (!$('#eventList').dataset.loaded) loadEvents();
    }, 2600);
  }

  /* rAF loop: scroll parallax, reveals, peek, progress */
  const wallEl = wall;

  function loop() {
    const y = window.scrollY;

    // reveal sweep (cheap: the selector empties out as things reveal)
    if (!sweepReveals.el) sweepReveals.el = 0;
    if ((sweepReveals.el++ & 3) === 0) sweepReveals();

    // progress line + nav state
    const docH = document.documentElement.scrollHeight - window.innerHeight;
    if (progressBar) progressBar.style.transform = 'scaleX(' + (docH > 0 ? clamp(y / docH, 0, 1) : 0) + ')';
    if (nav && !menuOpen) nav.classList.toggle('is-stuck', y > 40);

    // hero wall drifts slower than the page
    if (wallEl && !reduced && y < window.innerHeight * 1.2) {
      const p = clamp(y / Math.max(1, window.innerHeight), 0, 1.2);
      wallEl.style.transform = 'translate3d(0,' + (-p * 62).toFixed(2) + 'px,0) scale(' + (1 + p * 0.06).toFixed(3) + ')';
    }

    // peek lerp
    if (peek && peekState.on) {
      peekState.x += (peekState.tx - peekState.x) * 0.12;
      peekState.y += (peekState.ty - peekState.y) * 0.12;
      peek.style.left = peekState.x.toFixed(1) + 'px';
      peek.style.top = peekState.y.toFixed(1) + 'px';
    }

    requestAnimationFrame(loop);
  }

  if (finePointer && !reduced) {
    window.addEventListener('mousemove', (e) => {
      if (!peekState.on) return;
      peekState.tx = e.clientX;
      peekState.ty = e.clientY;
    }, { passive: true });
  }

  // hold the entrance until the type is actually ready (no half-painted masks)
  function scheduleEntrance() {
    if (document.fonts && document.fonts.ready) {
      document.fonts.ready.then(enter).catch(enter);
    }
    setTimeout(enter, 1100); // hard fallback, never leave the hero blank
  }

  function start() {
    boot();
    scheduleEntrance();
    requestAnimationFrame(loop);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
