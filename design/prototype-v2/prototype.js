(() => {
  'use strict';

  const $ = (selector, parent = document) => parent.querySelector(selector);
  const $$ = (selector, parent = document) => [...parent.querySelectorAll(selector)];
  const product = $('#product');
  const sources = ['Bilibili', '网易云', 'YouTube'];
  const titles = ['夜航', '夜航 · 城市现场', '夜航之后', '夜航 / Midnight Session'];
  const authors = ['拾音计划', '落日工作室', '慢速电台', 'Midnight Collective'];
  const durations = [222, 264, 198, 315];
  const tracks = sources.flatMap((source, sourceIndex) => titles.map((title, index) => ({
    id: `${sourceIndex}-${index}`, title, author: authors[index], duration: durations[index],
    source, art: index, type: source === '网易云' ? '歌曲' : '视频',
  })));
  const initial = () => ({
    device: matchMedia('(max-width:700px)').matches ? 'android' : 'desktop',
    theme: 'dark', route: 'search', source: 'Bilibili', scene: 'results',
    queries: Object.fromEntries(sources.map(source => [source, '夜航'])),
    favorites: ['0-0', '1-2'], recent: ['0-0', '2-1'],
    queue: ['0-0', '1-2', '2-1'], current: '0-0', playing: false, position: 82,
    libraryTab: 'favorites', playlist: null,
    playlists: [{name: '通勤路上', ids: ['0-0', '1-2', '2-1'], art: 0}, {name: '安静片刻', ids: ['1-0', '2-2'], art: 2}],
    connected: ['Bilibili'], artworkColor: true, repeat: false, shuffle: false, volume: 65,
    history: ['夜航', '午后纯音乐', '城市现场'], overlay: null, menuTrack: null,
  });
  let state = initial();
  let previousFocus = null;
  let previousFocusData = null;
  let previousFocusRegion = null;
  let toastTimer;
  const escape = value => String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const trackById = id => tracks.find(track => track.id === id);
  const current = () => trackById(state.current);
  const time = value => `${Math.floor(value / 60)}:${String(Math.floor(value % 60)).padStart(2, '0')}`;
  const symbol = (name, filled = false) => `<span class="symbol${filled ? ' filled' : ''}" aria-hidden="true">${name}</span>`;
  const art = (track, extra = '') => `<span class="art art-${track.art} ${extra}" aria-hidden="true"></span>`;
  const iconButton = (action, name, label, extra = '') => `<button type="button" class="icon-button" data-action="${action}" aria-label="${escape(label)}" data-tip="${escape(label)}" ${extra}>${symbol(name)}</button>`;
  const favoriteButton = track => `<button type="button" class="icon-button favorite-list" data-action="favorite" data-id="${track.id}" aria-label="收藏 ${escape(track.title)}" aria-pressed="${state.favorites.includes(track.id)}" data-tip="收藏">${symbol('favorite')}</button>`;

  function nav() {
    const items = [ ['search', 'search', '搜索'], ['library', 'library_music', '音乐库'], ['settings', 'settings', '设置'] ];
    return items.map(([route, icon, label]) => `<button class="nav-button" data-action="navigate" data-route="${route}" ${state.route === route ? 'aria-current="page"' : ''}><span class="nav-indicator">${symbol(icon)}</span><span>${label}</span></button>`).join('');
  }

  function row(track) {
    const selected = track.id === state.current;
    return `<article class="track-row ${selected ? 'current' : ''}">
      <button class="track-main" data-action="play-track" data-id="${track.id}" aria-label="播放 ${escape(track.title)}，${escape(track.author)}，${track.source}">
        ${art(track)}<span class="track-info"><span class="track-title">${escape(track.title)}</span><span class="track-sub">${escape(track.author)} · ${track.type}</span></span>
      </button><span class="track-source">${track.source}</span>
      ${selected && state.playing ? `<span class="symbol playing" aria-label="正在播放">graphic_eq</span>` : ''}
      <span class="track-meta">${time(track.duration)}</span>${favoriteButton(track)}
      ${iconButton('track-menu', 'more_horiz', `${track.title} 的更多操作`, `data-id="${track.id}"`)}
    </article>`;
  }

  function featured(track) {
    return `<section class="featured-result" aria-label="最佳匹配">
      <button class="featured-art" data-action="play-track" data-id="${track.id}" aria-label="播放 ${escape(track.title)}">${art(track)}</button>
      <div class="featured-copy"><span class="source-label">${symbol('music_note')}最佳匹配 · ${track.source}</span>
      <h2>${escape(track.title)}</h2><p>${escape(track.author)} · ${track.type} · ${time(track.duration)}</p>
      <button class="filled-button featured-play" data-action="play-track" data-id="${track.id}">${symbol('play_arrow', true)}播放</button></div>
      ${favoriteButton(track)}</section>`;
  }

  function sourceSelector() {
    return `<div class="sources" role="radiogroup" aria-label="搜索音源">${sources.map(source => `<button class="source-button" role="radio" aria-checked="${state.source === source}" data-action="source" data-source="${source}">${state.source === source ? symbol('check') : ''}${source}</button>`).join('')}</div>`;
  }

  function empty(icon, title, copy, action, label) {
    return `<div class="empty-state">${symbol(icon)}<h2>${title}</h2><p>${copy}</p><button class="filled-button" data-action="${action}">${label}</button></div>`;
  }

  function searchPage() {
    const query = state.queries[state.source];
    let content;
    if (state.scene === 'idle') {
      content = `<div class="section-head"><h2>最近搜索</h2><button class="text-button" data-action="clear-history">清空</button></div>
        <div class="chips">${state.history.map(term => `<button class="history-chip" data-action="history" data-query="${escape(term)}">${symbol('history')}${escape(term)}</button>`).join('') || '<p class="hint">搜过的内容会出现在这里。</p>'}</div>
        <div class="section-head"><h2>接着听</h2></div><div class="track-list">${state.recent.map(trackById).map(row).join('')}</div>`;
    } else if (state.scene === 'error') {
      content = empty('wifi', '暂时连接不上', '当前音源没有响应。你可以重试，或切换其他音源；已在播放的内容不会因此停止。', 'retry', '重新连接');
    } else if (state.scene === 'empty') {
      content = empty('search', '还没找到这段声音', '试试更短的关键词，或换一个音源。你也可以直接粘贴视频或歌曲链接。', 'clear-search', '重新搜索');
    } else {
      const results = tracks.filter(track => track.source === state.source && (!query.trim() || `${track.title} ${track.author}`.toLowerCase().includes(query.trim().toLowerCase())));
      const expired = state.scene === 'expired' ? `<div class="inline-banner">${symbol('login')}<div class="banner-copy">登录已过期<small>重新连接 ${state.source}，继续访问账号内的内容。</small></div><button class="text-button" data-action="account-settings">去登录</button></div>` : '';
      content = `${expired}${results.length ? featured(results[0]) : ''}<div class="section-head"><h2>全部结果</h2><small role="status">${results.length} 个${state.source === '网易云' ? '歌曲' : '视频'}</small></div><div class="track-list">${results.map(row).join('')}</div>
        <p class="hint">来自 ${state.source} · 保留原始标题与版本信息</p>`;
      if (!results.length) content = empty('search', '没有匹配的结果', '换个关键词，再发现一些好声音。', 'clear-search', '重新搜索');
    }
    return `<header class="page-head"><div><p class="eyebrow">RxPlayer / SEARCH</p><h1>循声而来<span class="heading-dot">.</span></h1><p class="page-intro">从三个音源，找到你的下一段旋律。</p></div>${iconButton('open-player', 'headphones', '展开播放器')}</header>
      <form class="search-form" id="search-form" role="search">
        <span id="search-leading" class="symbol" aria-hidden="true">search</span>
        <button type="button" id="search-back" class="icon-button" data-action="exit-search" aria-label="退出搜索输入" hidden>${symbol('arrow_back')}</button>
        <input id="search-input" aria-label="搜索歌曲、视频或内容链接" placeholder="歌曲、视频，或粘贴链接" value="${escape(state.scene === 'idle' ? '' : query)}" enterkeyhint="search" autocomplete="off">
        ${iconButton('clear-search', 'close', '清空搜索')}
        <button class="icon-button" type="submit" aria-label="执行搜索" data-tip="搜索">${symbol('search')}</button>
      </form>${sourceSelector()}${content}`;
  }

  function libraryPage() {
    const playlist = state.playlist === null ? null : state.playlists[state.playlist];
    if (playlist) return `<header class="page-head"><div><p class="eyebrow">本地歌单</p><h1>${escape(playlist.name)}</h1></div>${iconButton('library-back', 'arrow_back', '返回音乐库')}</header>
      <p class="page-caption">${playlist.ids.length} 首 · 混合音源</p><div class="paired-actions" role="group" aria-label="歌单播放"><button class="filled-button" data-action="play-playlist">${symbol('play_arrow', true)}播放全部</button><button class="tonal-button" data-action="shuffle-playlist">${symbol('shuffle')}随机播放</button></div><div class="track-list">${playlist.ids.map(trackById).map(row).join('')}</div>`;
    const tabs = [['favorites', '收藏'], ['playlists', '歌单'], ['recent', '最近播放']];
    let content;
    if (state.libraryTab === 'playlists') {
      content = `<div class="section-head"><h2>我的歌单</h2><button class="text-button" data-action="new-playlist">${symbol('add')}新建</button></div><div class="playlist-grid">${state.playlists.map((list, index) => `<button class="playlist-card" data-action="open-playlist" data-index="${index}">${art({art:list.art})}<span><strong>${escape(list.name)}</strong><small>${list.ids.length} 首 · 本地歌单</small></span></button>`).join('')}</div>`;
    } else {
      const ids = state.libraryTab === 'favorites' ? state.favorites : state.recent;
      content = ids.length ? `<div class="section-head"><h2>${state.libraryTab === 'favorites' ? '喜欢的声音' : '最近听过'}</h2><small>${ids.length} 首</small></div><div class="track-list">${ids.map(trackById).map(row).join('')}</div>` : empty('favorite', '把喜欢的声音留在这里', '在曲目旁点击收藏，就可以随时回来再听。', 'go-search', '去搜索');
    }
    return `<header class="page-head"><div><p class="eyebrow">YOUR COLLECTION</p><h1>音乐库<span class="heading-dot">.</span></h1></div>${iconButton('new-playlist', 'add', '新建歌单')}</header>
      <section class="collection-feature"><div><p class="eyebrow">属于你的声音</p><h2>喜欢的，<br>都在这里。</h2><p>${state.favorites.length} 首收藏 · 随时接着听</p></div><span class="collection-covers" aria-hidden="true">${art({art:2})}${art({art:0})}</span><div class="paired-actions" role="group" aria-label="收藏播放"><button class="filled-button" data-action="play-favorites" ${state.favorites.length ? '' : 'disabled'}>${symbol('play_arrow', true)}播放收藏</button><button class="tonal-button" data-action="shuffle-favorites" ${state.favorites.length ? '' : 'disabled'}>${symbol('shuffle')}随机播放</button></div></section>
      <div class="tabs" role="tablist" aria-label="音乐库分类">${tabs.map(([id, label]) => `<button class="tab" role="tab" aria-selected="${state.libraryTab === id}" data-action="library-tab" data-tab="${id}" id="tab-${id}" aria-controls="library-content">${label}</button>`).join('')}</div><section id="library-content" role="tabpanel" aria-labelledby="tab-${state.libraryTab}">${content}</section>`;
  }

  function settingsPage() {
    return `<header class="page-head"><div><p class="eyebrow">MAKE IT YOURS</p><h1>听你的<span class="heading-dot">.</span></h1><p class="page-intro">让每一次打开，都刚刚好。</p></div></header><div class="settings-columns"><section>
      <div class="section-head"><h2>外观</h2></div><div class="settings-group">
        <label class="settings-row"><span class="setting-icon">${symbol(state.theme === 'dark' ? 'dark_mode' : 'light_mode')}</span><span class="row-copy"><strong>主题</strong><small>选择舒服的明暗</small></span><select id="theme-select" aria-label="应用主题"><option value="light" ${state.theme === 'light' ? 'selected' : ''}>浅色</option><option value="dark" ${state.theme === 'dark' ? 'selected' : ''}>深色</option></select></label>
        <label class="settings-row"><span class="setting-icon">${symbol('album')}</span><span class="row-copy"><strong>播放页强调色</strong><small>为正在听的声音留一片底色</small></span><span class="switch"><input type="checkbox" id="artwork-switch" aria-label="播放页强调色" ${state.artworkColor ? 'checked' : ''}></span></label>
      </div><div class="section-head"><h2>播放</h2></div><div class="settings-group"><div class="settings-row"><span class="setting-icon">${symbol('history')}</span><span class="row-copy"><strong>恢复播放位置</strong><small>打开时保持暂停，由你决定何时继续</small></span>${symbol('check')}</div></div></section><section><div class="section-head"><h2>音源账号</h2><small>原型演示</small></div><div class="settings-group">${sources.map(source => `<div class="settings-row"><span class="setting-icon">${symbol('music_note')}</span><div class="row-copy"><strong>${source}</strong><small>${state.connected.includes(source) ? '<span class="account-tag">已连接</span> · 听风的人' : '未连接'}</small></div><button class="text-button" data-action="connect" data-source="${source}">${state.connected.includes(source) ? '断开' : '连接'}</button></div>`).join('')}</div><p class="hint">账号操作仅作界面演示，未连接任何平台。</p></section></div><p class="settings-signature">Rx.<span>让每一段声音，都有自己的位置。</span></p>`;
  }

  function queueItems(omitCurrent = false) {
    return state.queue.filter(id => !omitCurrent || id !== state.current).map((id, index) => {
      const track = trackById(id);
      return `<div class="queue-item"><span class="queue-number">${index + 1}</span><button class="now-track" data-action="play-track" data-id="${id}" aria-label="播放 ${escape(track.title)}">${art(track)}<span class="track-info"><span class="track-title">${escape(track.title)}</span><span class="track-sub">${track.source} · ${time(track.duration)}</span></span></button>${iconButton('remove-queue', 'close', `移除 ${track.title}`, `data-id="${id}"`)}</div>`;
    }).join('') || '<p class="queue-caption">队列还空着，把想听的内容加入进来。</p>';
  }

  function supportPane() {
    const track = current();
    return `<div class="support-heading"><h2>播放队列</h2><span class="hint">${state.queue.length} 首</span></div>
      <div class="support-label">${state.playing ? '正在播放' : '已暂停'}</div><button class="now-track support-current" data-action="open-player" aria-label="展开正在播放">${art(track)}<span class="track-info"><span class="track-title">${escape(track.title)}</span><span class="track-sub">${escape(track.author)}</span></span>${symbol('open_in_full')}</button>
      <hr class="support-divider"><p class="eyebrow">队列中的其他曲目</p>${queueItems(true)}
      <p class="queue-caption">不同音源，同一段旅程。<br>你的队列可以收纳所有想听的声音。</p>`;
  }

  function playButton(extra = '') {
    return `<button class="play-button ${state.playing ? 'is-playing' : ''} ${extra}" data-action="toggle-play" aria-label="${state.playing ? '暂停' : '播放'}">${symbol(state.playing ? 'pause' : 'play_arrow', true)}<span class="hero-play-label">${state.playing ? '暂停' : '播放'}</span></button>`;
  }

  function progress() {
    return `<div class="progress-line"><span data-position>${time(state.position)}</span><input type="range" min="0" max="${current().duration}" value="${state.position}" style="--progress:${state.position / current().duration * 100}%" aria-label="播放进度" aria-valuetext="${time(state.position)} / ${time(current().duration)}" data-progress><span>${time(current().duration)}</span></div>`;
  }

  function modeButton(action, icon, label, selected) {
    return `<button class="mode-toggle" data-action="${action}" aria-label="${label}" aria-pressed="${selected}">${symbol(icon)}<span>${label}</span><span class="mode-mark" aria-hidden="true">${selected ? '开' : ''}</span></button>`;
  }

  function transport() {
    const track = current();
    return `<button class="now-track" data-action="open-player" aria-label="展开 ${escape(track.title)} 的播放器">${art(track, 'mini')}<span class="track-info"><span class="track-title">${escape(track.title)}</span><span class="track-sub">${state.playing ? '正在播放' : '已暂停'} · ${track.source}</span></span></button>
      <div class="transport-center"><div class="play-controls">${iconButton('shuffle','shuffle','随机播放',`aria-pressed="${state.shuffle}"`)}${iconButton('previous','skip_previous','上一首')}${playButton()}${iconButton('next','skip_next','下一首')}${iconButton('repeat','repeat','列表循环',`aria-pressed="${state.repeat}"`)}</div>${progress()}</div>
      <div class="transport-right"><span class="symbol volume-icon" aria-hidden="true">volume_up</span><input class="volume" type="range" min="0" max="100" value="${state.volume}" style="--progress:${state.volume}%" aria-label="音量">${iconButton('open-queue','queue_music','播放队列')}</div>
      ${playButton('mini-play')}<button class="icon-button mini-queue" data-action="open-queue" aria-label="播放队列">${symbol('queue_music')}</button>`;
  }

  function render() {
    const active = document.activeElement;
    const focusedData = product.contains(active) && active.dataset.action && !state.overlay ? {...active.dataset} : null;
    product.dataset.device = state.device;
    product.dataset.theme = state.theme;
    product.dataset.playing = state.playing;
    product.classList.remove('is-searching');
    $('#rail').innerHTML = '<div class="brand" aria-label="RxPlayer">Rx.</div>' + nav();
    $('#bottom-nav').innerHTML = nav();
    $('#main-pane').innerHTML = state.route === 'search' ? searchPage() : state.route === 'library' ? libraryPage() : settingsPage();
    $('#support-pane').innerHTML = supportPane();
    $('#transport').innerHTML = transport();
    $('#scene-select').value = state.scene;
    $$('.device-picker button').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.deviceChoice === state.device)));
    $('#review-theme').innerHTML = symbol(state.theme === 'light' ? 'dark_mode' : 'light_mode');
    if (state.overlay) renderOverlay();
    else if (focusedData) {
      $$('[data-action]', product).find(element => Object.entries(focusedData).every(([key,value]) => element.dataset[key] === value) && element.getClientRects().length)?.focus({preventScroll:true});
    }
  }

  function toast(message) {
    const snackbar = $('#snackbar');
    clearTimeout(toastTimer);
    snackbar.textContent = message;
    snackbar.hidden = false;
    toastTimer = setTimeout(() => { snackbar.hidden = true; }, 3000);
  }

  function openOverlay(type, trackId) {
    const entering = !state.overlay;
    previousFocus = document.activeElement;
    previousFocusData = previousFocus.dataset.action ? {...previousFocus.dataset} : null;
    previousFocusRegion = previousFocus.closest('#transport,#main-pane,#rail,#bottom-nav')?.id;
    state.overlay = type;
    if (trackId) state.menuTrack = trackId;
    renderOverlay();
    for (const element of [$('.workspace'), $('#transport'), $('#bottom-nav')]) element.inert = true;
    $('#overlay').hidden = false;
    if (entering && !matchMedia('(prefers-reduced-motion:reduce)').matches) {
      $('#dialog').animate([{transform:'translateY(32px) scale(.97)'},{transform:'translateY(0) scale(1)'}], {duration:500,easing:'cubic-bezier(.38,1.21,.22,1)'});
      $('#dialog').animate([{opacity:0},{opacity:1}], {duration:200,easing:'cubic-bezier(.34,.8,.34,1)'});
    }
    requestAnimationFrame(() => $('#dialog input, #dialog button')?.focus());
  }

  function closeOverlay() {
    if (!state.overlay) return;
    state.overlay = null;
    $('#overlay').hidden = true;
    for (const element of [$('.workspace'), $('#transport'), $('#bottom-nav')]) element.inert = false;
    const replacement = previousFocusData ? $$('[data-action]', previousFocusRegion ? $(`#${previousFocusRegion}`) : product).find(element => Object.entries(previousFocusData).every(([key,value]) => element.dataset[key] === value) && element.getClientRects().length) : null;
    if (previousFocus?.isConnected && previousFocus.getClientRects().length) previousFocus.focus({preventScroll:true});
    else if (replacement) replacement.focus({preventScroll:true});
    else $$('#main-pane button, #transport button').find(element => element.getClientRects().length > 0)?.focus({preventScroll:true});
  }

  function renderOverlay() {
    const dialog = $('#dialog');
    const wasInDialog = dialog.contains(document.activeElement);
    const focusedAction = wasInDialog ? document.activeElement.dataset.action : null;
    dialog.className = `sheet ${state.overlay === 'player' ? `full-player${state.artworkColor ? ' artwork-tone' : ''}` : ''}`;
    let title = '';
    let body = '';
    if (state.overlay === 'player') {
      const track = current();
      title = state.playing ? '正在播放' : '已暂停';
      body = `<div class="player-layout"><div class="player-art-column"><div class="art-frame">${art(track, 'large')}</div><p class="art-caption">${escape(track.author)} / ORIGINAL SESSIONS</p></div><div class="player-detail"><span class="source-label">${symbol('music_note')}${track.source} · ${track.type}</span><div class="player-title-row"><div><h3 class="player-title">${escape(track.title)}</h3><p class="player-sub">${escape(track.author)}</p></div>${favoriteButton(track)}</div><div class="hero-controls">${progress()}<div class="play-controls" role="group" aria-label="播放操作"><button class="icon-button soft-skip" data-action="previous" aria-label="上一首" data-tip="上一首">${symbol('skip_previous', true)}</button>${playButton()}<button class="icon-button soft-skip" data-action="next" aria-label="下一首" data-tip="下一首">${symbol('skip_next', true)}</button></div></div><div class="player-secondary">${modeButton('shuffle','shuffle','随机',state.shuffle)}${modeButton('repeat','repeat','循环',state.repeat)}<button class="queue-pill" data-action="open-queue" aria-label="播放队列，${state.queue.length} 首">${symbol('queue_music')}<span class="queue-label">队列</span><span class="queue-count">${state.queue.length}</span></button></div></div></div>`;
    } else if (state.overlay === 'queue') {
      title = `播放队列 · ${state.queue.length}`;
      body = queueItems();
    } else if (state.overlay === 'menu') {
      const track = trackById(state.menuTrack);
      title = track.title;
      body = `<p class="hint">${escape(track.author)} · ${track.source}</p>
        <button class="menu-action" data-action="enqueue-next" data-id="${track.id}">${symbol('skip_next')}下一首播放</button>
        <button class="menu-action" data-action="enqueue" data-id="${track.id}">${symbol('queue_music')}加入播放队列</button>
        <button class="menu-action" data-action="favorite" data-id="${track.id}">${symbol('favorite', state.favorites.includes(track.id))}${state.favorites.includes(track.id) ? '取消收藏' : '收藏'}</button>
        <button class="menu-action" data-action="choose-playlist" data-id="${track.id}">${symbol('playlist_add')}添加到歌单</button>`;
    } else if (state.overlay === 'new-playlist') {
      title = '新建歌单';
      body = '<form id="playlist-form"><label for="playlist-name">歌单名称</label><input class="dialog-input" id="playlist-name" placeholder="给这段旅程起个名字" required maxlength="40"><div class="dialog-actions"><button class="text-button" type="button" data-action="close-overlay">取消</button><button class="filled-button" type="submit">创建</button></div></form>';
    } else if (state.overlay === 'link') {
      title = state.linkSource ? `识别到 ${state.linkSource} 链接` : '暂不支持这个链接';
      body = `<p class="hint">${escape(state.queries[state.source])}</p><p>暂时无法读取内容信息。请检查链接，或返回关键词搜索。</p><div class="dialog-actions"><button class="filled-button" data-action="close-overlay">返回搜索</button></div>`;
    } else if (state.overlay === 'choose-playlist') {
      title = '添加到歌单';
      body = state.playlists.map((playlist, index) => `<button class="menu-action" data-action="add-to-playlist" data-index="${index}">${symbol('album')}${escape(playlist.name)}</button>`).join('');
    }
    dialog.innerHTML = `<header class="sheet-head"><h2 id="dialog-title">${escape(title)}</h2>${iconButton('close-overlay', state.overlay === 'player' ? 'expand_more' : 'close', '关闭')}</header>${body}`;
    if (wasInDialog) requestAnimationFrame(() => {
      const target = focusedAction ? $(`[data-action="${focusedAction}"]`, dialog) : null;
      (target || $('button,input,select', dialog))?.focus();
    });
  }

  function playTrack(id) {
    state.current = id;
    state.position = 0;
    state.playing = true;
    if (!state.queue.includes(id)) state.queue.push(id);
    state.recent = [id, ...state.recent.filter(item => item !== id)];
    render();
  }

  function step(offset) {
    if (!state.queue.length) return;
    const index = state.queue.indexOf(state.current);
    const nextIndex = state.shuffle && state.queue.length > 1
      ? (index + 1 + Math.floor(Math.random() * (state.queue.length - 1))) % state.queue.length
      : (index + offset + state.queue.length) % state.queue.length;
    playTrack(state.queue[nextIndex]);
  }

  document.addEventListener('click', event => {
    const deviceChoice = event.target.closest('[data-device-choice]');
    if (deviceChoice) { closeOverlay(); state.device = deviceChoice.dataset.deviceChoice; render(); return; }
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const {action, id, source} = button.dataset;
    switch (action) {
      case 'navigate': closeOverlay(); state.route = button.dataset.route; state.playlist = null; render(); break;
      case 'source': state.queries[state.source] = $('#search-input').value; state.source = source; render(); break;
      case 'go-search': state.route = 'search'; render(); break;
      case 'history': state.queries[state.source] = button.dataset.query; state.scene = 'results'; render(); break;
      case 'clear-history': state.history = []; render(); break;
      case 'clear-search': state.queries[state.source] = ''; state.scene = 'idle'; render(); $('#search-input')?.focus(); break;
      case 'exit-search': product.classList.remove('is-searching'); $('#search-back').hidden = true; $('#search-leading').hidden = false; $('#search-input').blur(); break;
      case 'retry': state.scene = 'results'; render(); break;
      case 'play-track': playTrack(id); break;
      case 'toggle-play': state.playing = !state.playing; if (state.position >= current().duration) state.position = 0; render(); break;
      case 'next': step(1); break;
      case 'previous': step(-1); break;
      case 'favorite': state.favorites = state.favorites.includes(id) ? state.favorites.filter(item => item !== id) : [...state.favorites, id]; render(); toast(state.favorites.includes(id) ? '已收藏' : '已取消收藏'); break;
      case 'track-menu': openOverlay('menu', id); break;
      case 'open-player': openOverlay('player'); break;
      case 'open-queue': openOverlay('queue'); break;
      case 'close-overlay': closeOverlay(); break;
      case 'enqueue': case 'enqueue-next': {
        if (id === state.current && state.queue.includes(id)) { closeOverlay(); toast('当前曲目已在队列中'); break; }
        state.queue = state.queue.filter(item => item !== id || item === state.current);
        if (action === 'enqueue-next') state.queue.splice(Math.max(0, state.queue.indexOf(state.current) + 1), 0, id);
        else if (!state.queue.includes(id)) state.queue.push(id);
        closeOverlay(); render(); toast(action === 'enqueue' ? '已加入播放队列' : '将下一首播放'); break;
      }
      case 'remove-queue': {
        state.queue = state.queue.filter(item => item !== id);
        if (id === state.current && state.queue.length) { state.current = state.queue[0]; state.position = 0; }
        else if (!state.queue.length) state.playing = false;
        render(); break;
      }
      case 'shuffle': state.shuffle = !state.shuffle; render(); toast(state.shuffle ? '已开启随机播放' : '已关闭随机播放'); break;
      case 'repeat': state.repeat = !state.repeat; render(); toast(state.repeat ? '已开启列表循环' : '已关闭列表循环'); break;
      case 'library-tab': state.libraryTab = button.dataset.tab; render(); break;
      case 'new-playlist': openOverlay('new-playlist'); break;
      case 'open-playlist': state.playlist = Number(button.dataset.index); render(); break;
      case 'library-back': state.playlist = null; render(); break;
      case 'play-playlist': {
        state.queue = [...state.playlists[state.playlist].ids];
        state.shuffle = false;
        if (state.queue.length) playTrack(state.queue[0]);
        else toast('先向歌单添加一些声音');
        break;
      }
      case 'play-favorites': {
        if (!state.favorites.length) break;
        state.queue = [...state.favorites]; state.shuffle = false; playTrack(state.queue[0]); break;
      }
      case 'shuffle-favorites': {
        if (!state.favorites.length) break;
        state.queue = [...state.favorites]; state.shuffle = true;
        playTrack(state.queue[Math.floor(Math.random() * state.queue.length)]); break;
      }
      case 'shuffle-playlist': {
        state.queue = [...state.playlists[state.playlist].ids];
        if (state.queue.length) { state.shuffle = true; playTrack(state.queue[Math.floor(Math.random() * state.queue.length)]); }
        else toast('先向歌单添加一些声音');
        break;
      }
      case 'choose-playlist': state.menuTrack = id; openOverlay('choose-playlist'); break;
      case 'add-to-playlist': {
        const list = state.playlists[Number(button.dataset.index)];
        if (!list.ids.includes(state.menuTrack)) list.ids.push(state.menuTrack);
        closeOverlay(); render(); toast(`已添加到「${list.name}」`); break;
      }
      case 'account-settings': state.route = 'settings'; render(); break;
      case 'connect': state.connected = state.connected.includes(source) ? state.connected.filter(item => item !== source) : [...state.connected, source]; render(); toast(`${source} ${state.connected.includes(source) ? '已连接' : '已断开'}（原型演示）`); break;
    }
  });

  document.addEventListener('submit', event => {
    if (event.target.id === 'search-form') {
      event.preventDefault();
      const query = $('#search-input').value.trim();
      state.queries[state.source] = query;
      if (/https?:\/\//i.test(query)) {
        let host = '';
        try { host = new URL(query).hostname.toLowerCase(); } catch { /* Show unsupported-link state. */ }
        const matches = domain => host === domain || host.endsWith(`.${domain}`);
        state.linkSource = matches('bilibili.com') || matches('b23.tv') ? 'Bilibili'
          : matches('youtube.com') || matches('youtu.be') ? 'YouTube'
          : matches('music.163.com') ? '网易云' : null;
        openOverlay('link');
      } else {
        state.scene = query ? 'results' : 'idle';
        if (query) state.history = [query, ...state.history.filter(item => item !== query)].slice(0, 6);
        render();
      }
    }
    if (event.target.id === 'playlist-form') {
      event.preventDefault();
      const name = $('#playlist-name').value.trim();
      if (!name) return;
      state.playlists.push({name, ids: [], art: state.playlists.length % 4});
      state.route = 'library'; state.libraryTab = 'playlists'; state.playlist = null;
      closeOverlay(); render(); toast('歌单已创建');
    }
  });

  document.addEventListener('focusin', event => {
    if (event.target.id === 'search-input') {
      product.classList.add('is-searching');
      $('#search-back').hidden = false;
      $('#search-leading').hidden = true;
    }
  });

  document.addEventListener('change', event => {
    if (event.target.id === 'scene-select') { closeOverlay(); state.route = 'search'; state.scene = event.target.value; render(); }
    if (event.target.id === 'theme-select') { state.theme = event.target.value; render(); }
    if (event.target.id === 'artwork-switch') { state.artworkColor = event.target.checked; if (state.overlay === 'player') renderOverlay(); toast('播放页配色偏好已更新'); }
  });

  document.addEventListener('input', event => {
    if (event.target.matches('.volume')) { state.volume = Number(event.target.value); event.target.style.setProperty('--progress', `${state.volume}%`); }
    if (event.target.matches('[data-progress]')) {
      state.position = Number(event.target.value);
      $$('[data-position]').forEach(element => { element.textContent = time(state.position); });
      $$('[data-progress]').forEach(element => {
        element.value = state.position;
        element.style.setProperty('--progress', `${state.position / current().duration * 100}%`);
        element.setAttribute('aria-valuetext', `${time(state.position)} / ${time(current().duration)}`);
      });
    }
  });

  document.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      if (state.overlay) { event.preventDefault(); closeOverlay(); }
      else if (document.activeElement?.id === 'search-input') { event.preventDefault(); $('[data-action="exit-search"]').click(); }
      return;
    }
    if (event.key === 'Tab' && state.overlay) {
      const controls = $$('button,input,select,a[href]', $('#dialog')).filter(element => !element.disabled);
      const first = controls[0], last = controls.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      return;
    }
    const input = document.activeElement?.matches('input,textarea,select,[contenteditable=true]');
    const focusedButton = document.activeElement?.tagName === 'BUTTON';
    if (event.code === 'Space' && !input && !focusedButton && !state.overlay) { event.preventDefault(); state.playing = !state.playing; render(); }
    if (['ArrowLeft','ArrowRight'].includes(event.key) && event.target instanceof Element && event.target.closest('.sources')) {
      event.preventDefault();
      state.source = sources[(sources.indexOf(state.source) + (event.key === 'ArrowRight' ? 1 : 2)) % 3];
      render(); $(`.source-button[data-source="${state.source}"]`).focus();
    }
  });

  $('#review-theme').addEventListener('click', () => { state.theme = state.theme === 'light' ? 'dark' : 'light'; render(); });
  $('#review-player').addEventListener('click', () => openOverlay('player'));
  $('#review-reset').addEventListener('click', () => { closeOverlay(); state = initial(); render(); toast('已恢复原型初始状态'); });
  setInterval(() => {
    if (!state.playing) return;
    if (state.position >= current().duration) {
      const index = state.queue.indexOf(state.current);
      if (state.repeat || index < state.queue.length - 1) step(1);
      else { state.playing = false; render(); }
      return;
    }
    state.position += 1;
    $$('[data-position]').forEach(element => { element.textContent = time(state.position); });
    $$('[data-progress]').filter(element => element !== document.activeElement).forEach(element => {
      element.value = state.position;
      element.style.setProperty('--progress', `${state.position / current().duration * 100}%`);
      element.setAttribute('aria-valuetext', `${time(state.position)} / ${time(current().duration)}`);
    });
  }, 1000);
  render();
  if (new URLSearchParams(location.search).get('view') === 'player') openOverlay('player');
})();
