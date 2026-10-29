(() => {
  'use strict';

  const $ = (selector, parent = document) => parent.querySelector(selector);
  const $$ = (selector, parent = document) => [...parent.querySelectorAll(selector)];
  const product = $('#product');
  const queueBreakpoint = matchMedia('(min-width:1101px)');
  const sources = ['Bilibili', '网易云', 'YouTube'];
  const titles = ['夜航', '夜航 · 城市现场', '夜航之后', '夜航 / Midnight Session'];
  const authors = ['拾音计划', '落日工作室', '慢速电台', 'Midnight Collective'];
  const durations = [222, 264, 198, 315];
  // Original demonstration lyrics, not fetched from an online lyric service.
  const demoLyrics = [
    ['风把街灯轻轻点亮','沿着夜色慢慢启航','把喧嚣留在远方','让旋律陪我去流浪','下一站有温柔的光','我们迎着清晨歌唱'],
    ['人群散去灯还亮着','晚风穿过这座城市','我们把心事唱成歌','让回声停在这一刻','走过长街再看星河','明天依然有你和我'],
    [],
    ['夜色落在安静的窗','收起白天匆忙的行囊','让心跳跟着节奏轻晃','听见远方温柔回响','把此刻写进新的乐章','陪你走到天色微亮'],
  ];
  const tracks = sources.flatMap((source, sourceIndex) => titles.map((title, index) => ({
    id: `${sourceIndex}-${index}`, title, author: authors[index], duration: durations[index],
    source, art: index, hasLyrics: demoLyrics[index].length > 0, type: source === '网易云' ? '歌曲' : '视频',
  })));
  const initial = () => ({
    device: matchMedia('(max-width:700px)').matches ? 'android' : 'desktop',
    theme: 'dark', route: 'search', source: 'Bilibili', scene: 'results',
    queries: Object.fromEntries(sources.map(source => [source, '夜航'])),
    favorites: ['0-0', '1-2'], recent: ['0-0', '2-1'],
    queue: ['0-0', '1-2', '2-1'], queueOpen: queueBreakpoint.matches, current: '0-0', playing: false, position: 82,
    phase: 'paused', prepared: true, wantPlay: false,
    libraryTab: 'favorites', playlist: null,
    playlists: [{name: '通勤路上', ids: ['0-0', '1-2', '2-1'], art: 0}, {name: '安静片刻', ids: ['1-0', '2-2'], art: 2}],
    connected: ['Bilibili'], artworkColor: true, lyricsLayout: 'auto', repeat: false, shuffle: false, volume: 65,
    history: ['夜航', '午后纯音乐', '城市现场'], overlay: null, menuTrack: null,
  });
  let state = initial();
  let previousFocus = null;
  let previousFocusData = null;
  let previousFocusRegion = null;
  let queueFocusOrigin = null;
  let toastTimer;
  let searchTimer, playbackTimer, searchTicket = 0, playbackTicket = 0;
  const escape = value => String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
  const trackById = id => tracks.find(track => track.id === id);
  const current = () => trackById(state.current);
  const time = value => `${Math.floor(value / 60)}:${String(Math.floor(value % 60)).padStart(2, '0')}`;
  const stateIcons = new Set(['check_circle','hourglass_top','link_off','lock','refresh','replay','sync','wifi_off']);
  const symbol = (name, filled = false) => `<span class="symbol${stateIcons.has(name) ? ' state-symbol' : ''}${filled ? ' filled' : ''}" aria-hidden="true">${name}</span>`;
  const art = (track, extra = '') => `<span class="art art-${track.art} ${extra}" aria-hidden="true"></span>`;
  const iconButton = (action, name, label, extra = '') => `<button type="button" class="icon-button" data-action="${action}" aria-label="${escape(label)}" data-tip="${escape(label)}" ${extra}>${symbol(name)}</button>`;
  const favoriteButton = track => `<button type="button" class="icon-button favorite-list" data-action="favorite" data-id="${track.id}" aria-label="收藏 ${escape(track.title)}" aria-pressed="${state.favorites.includes(track.id)}" data-tip="收藏">${symbol('favorite')}</button>`;
  const focusKey = element => Object.fromEntries(['action','id','source','route','tab','index'].filter(key => element.dataset[key] !== undefined).map(key => [key,element.dataset[key]]));
  const waitingPhases = ['preparing','buffering','refreshing'];
  const phaseLabels = {paused:'已暂停',playing:'正在播放',preparing:'正在准备音频',buffering:'正在缓冲',failed:'播放失败',unavailable:'内容不可用',expired:'播放地址已失效',refreshing:'正在重新获取地址',ended:'已播放完'};
  const canSeek = () => state.prepared && ['paused','playing'].includes(state.phase);
  const announce = message => { $('#live-feedback').textContent = message; };
  const pendingBar = label => `<div class="state-progress" role="progressbar" aria-label="${escape(label)}"><span></span></div>`;

  function cancelSearch() { clearTimeout(searchTimer); searchTicket += 1; }
  function cancelPlayback() { clearTimeout(playbackTimer); playbackTicket += 1; }
  const playbackMessages = {failed:'播放失败，点击播放按钮重试',unavailable:'当前内容不可播放，可切换曲目或查看音源账号',expired:'播放地址已失效，点击播放按钮重新获取'};
  function setPhase(phase) {
    const changed = state.phase !== phase;
    state.phase = phase; state.playing = phase === 'playing';
    if (changed) {
      clearTimeout(toastTimer); $('#snackbar').hidden = true;
      if (playbackMessages[phase]) toast(playbackMessages[phase]);
    }
  }
  function rememberPlayed() {
    if (!state.queue.includes(state.current)) state.queue.push(state.current);
    state.recent = [state.current,...state.recent.filter(id => id !== state.current)];
  }

  function runSearch() {
    const fromControl = product.contains(document.activeElement) && document.activeElement.id !== 'search-input';
    cancelSearch();
    state.scene = 'loading';
    const ticket = searchTicket, source = state.source;
    render(); announce(`正在搜索 ${source}`);
    if (fromControl) $('[data-action="cancel-search"]')?.focus({preventScroll:true});
    searchTimer = setTimeout(() => {
      if (ticket !== searchTicket || source !== state.source || state.scene !== 'loading') return;
      finishSearch();
    }, 900);
  }

  function finishSearch() {
    const fromCancel = document.activeElement.dataset.action === 'cancel-search';
    cancelSearch(); state.scene = 'results'; render();
    if (fromCancel) $('#search-input')?.focus({preventScroll:true});
    announce(`${state.source} 搜索已完成`);
  }

  function finishPlayback() {
    cancelPlayback(); state.prepared = true;
    setPhase(state.wantPlay ? 'playing' : 'paused');
    if (state.playing) rememberPlayed();
    render(); announce(state.playing ? '音频已就绪，继续播放' : '音频已就绪，保持暂停');
  }

  function preparePlayback(phase, wantPlay = state.wantPlay) {
    cancelPlayback(); state.wantPlay = wantPlay; setPhase(phase);
    const ticket = playbackTicket, id = state.current;
    render(); announce(phaseLabels[phase]);
    playbackTimer = setTimeout(() => {
      if (ticket !== playbackTicket || state.current !== id || state.phase !== phase) return;
      finishPlayback();
    }, 1100);
  }

  function stopWaiting() {
    const phase = state.phase;
    cancelPlayback(); state.wantPlay = false;
    setPhase(phase === 'refreshing' ? 'expired' : 'paused');
    render(); announce(phase === 'refreshing' ? '已取消重新获取地址' : '已暂停，保持当前曲目');
  }

  function togglePlayback() {
    if (waitingPhases.includes(state.phase)) { stopWaiting(); return; }
    if (state.phase === 'unavailable') { toast(playbackMessages.unavailable); return; }
    if (state.phase === 'expired') { preparePlayback('refreshing'); return; }
    if (state.phase === 'failed') { preparePlayback('preparing',true); return; }
    if (state.phase === 'ended') { state.position = 0; state.prepared = false; }
    if (!state.prepared) { preparePlayback('preparing',true); return; }
    state.wantPlay = !state.playing;
    setPhase(state.wantPlay ? 'playing' : 'paused');
    if (state.playing) rememberPlayed();
    render();
  }

  function previewPhase(phase) {
    cancelPlayback();
    if (phase === 'playing') { state.prepared = true; state.wantPlay = true; }
    if (phase === 'paused') { state.prepared = true; state.wantPlay = false; }
    if (phase === 'preparing') { state.prepared = false; state.position = 0; state.wantPlay = true; }
    if (phase === 'buffering') { state.prepared = true; state.wantPlay = true; }
    if (phase === 'ended') { state.prepared = true; state.position = current().duration; state.wantPlay = false; }
    setPhase(phase);
  }

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
      </div><button class="icon-button featured-play" data-action="play-track" data-id="${track.id}" aria-label="播放 ${escape(track.title)}" data-tip="播放">${symbol('play_arrow', true)}</button></section>`;
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
    } else if (state.scene === 'loading') {
      content = `<section class="search-wait state-card" aria-busy="true" aria-label="搜索结果"><div class="state-card-content">${symbol('search')}<div><h2>正在搜索 ${state.source}</h2><p>正在查找「${escape(query)}」。当前播放不受影响。</p></div></div>${pendingBar(`正在搜索 ${state.source}`)}<div class="state-actions"><button class="tonal-button" data-action="cancel-search">取消搜索</button></div></section>`;
    } else if (state.scene === 'error') {
      content = empty('wifi', '暂时连接不上', '当前音源没有响应。你可以重试，或切换其他音源；已在播放的内容不会因此停止。', 'retry', '重新连接');
    } else if (state.scene === 'empty') {
      content = empty('search', '还没找到这段声音', '试试更短的关键词，或换一个音源。你也可以直接粘贴视频或歌曲链接。', 'clear-search', '重新搜索');
    } else {
      const results = tracks.filter(track => track.source === state.source && (!query.trim() || `${track.title} ${track.author}`.toLowerCase().includes(query.trim().toLowerCase())));
      const expired = state.scene === 'expired' ? `<div class="inline-banner">${symbol('login')}<div class="banner-copy">账号连接已过期<small>匿名可用的内容仍可搜索；账号内内容需要重新连接。</small></div><button class="text-button" data-action="account-settings">查看账号</button></div>` : '';
      content = `${expired}<div class="section-head results-heading"><h2>搜索结果</h2><small role="status">${results.length} 个${state.source === '网易云' ? '歌曲' : '视频'}</small></div>${results.length ? featured(results[0]) : ''}${results.length > 1 ? `<div class="section-head"><h2>更多匹配</h2></div><div class="track-list">${results.slice(1).map(row).join('')}</div>` : ''}
        <p class="hint">来自 ${state.source} · 保留原始标题与版本信息</p>`;
      if (!results.length) content = empty('search', '没有匹配的结果', '换个关键词，再发现一些好声音。', 'clear-search', '重新搜索');
    }
    return `<header class="page-head"><div><p class="eyebrow">RxPlayer</p><h1>搜索</h1><p class="page-intro">歌曲、视频，从这里开始听。</p></div>${iconButton('open-player', 'headphones', '展开播放器')}</header>
      <form class="search-form" id="search-form" role="search">
        <span id="search-leading" class="symbol" aria-hidden="true">search</span>
        <button type="button" id="search-back" class="icon-button" data-action="exit-search" aria-label="退出搜索输入" hidden>${symbol('arrow_back')}</button>
        <input id="search-input" aria-label="搜索歌曲、视频或内容链接" placeholder="歌曲、视频，或粘贴链接" value="${escape(query)}" enterkeyhint="search" autocomplete="off">
        ${iconButton('clear-search', 'close', '清空搜索')}
        <button class="icon-button" type="submit" aria-label="执行搜索" data-tip="搜索">${symbol('search')}</button>
      </form>${sourceSelector()}${content}`;
  }

  function libraryPage() {
    const playlist = state.playlist === null ? null : state.playlists[state.playlist];
    if (playlist) return `<header class="page-head"><div><p class="eyebrow">本地歌单</p><h1>${escape(playlist.name)}</h1></div>${iconButton('library-back', 'arrow_back', '返回音乐库')}</header>
      <p class="page-caption">${playlist.ids.length} 首 · 混合音源</p><div class="paired-actions"><button class="filled-button" data-action="play-playlist">${symbol('play_arrow', true)}播放全部</button><button class="icon-button tonal-action" data-action="shuffle-playlist" aria-label="随机播放歌单" data-tip="随机播放">${symbol('shuffle')}</button></div><div class="track-list">${playlist.ids.map(trackById).map(row).join('')}</div>`;
    const tabs = [['favorites', '收藏'], ['playlists', '歌单'], ['recent', '最近播放']];
    let content;
    if (state.libraryTab === 'playlists') {
      content = `<div class="section-head"><h2>我的歌单</h2><button class="text-button" data-action="new-playlist">${symbol('add')}新建</button></div><div class="playlist-grid">${state.playlists.map((list, index) => `<button class="playlist-card" data-action="open-playlist" data-index="${index}">${art({art:list.art})}<span><strong>${escape(list.name)}</strong><small>${list.ids.length} 首 · 本地歌单</small></span></button>`).join('')}</div>`;
    } else {
      const ids = state.libraryTab === 'favorites' ? state.favorites : state.recent;
      content = ids.length ? `<div class="section-head"><h2>${state.libraryTab === 'favorites' ? '喜欢的声音' : '最近听过'}</h2><small>${ids.length} 首</small></div><div class="track-list">${ids.map(trackById).map(row).join('')}</div>` : empty('favorite', '把喜欢的声音留在这里', '在曲目旁点击收藏，就可以随时回来再听。', 'go-search', '去搜索');
    }
    return `<header class="page-head"><div><p class="eyebrow">为喜欢的声音留个位置</p><h1>音乐库</h1></div>${iconButton('new-playlist', 'add', '新建歌单')}</header>
      <section class="collection-summary"><span class="collection-mark">${symbol('favorite',true)}</span><div><h2>喜欢的声音</h2><p>${state.favorites.length} 首收藏 · 跨音源收纳</p></div><button class="icon-button summary-play" data-action="play-favorites" aria-label="播放收藏" data-tip="播放收藏" ${state.favorites.length ? '' : 'disabled'}>${symbol('play_arrow', true)}</button></section>
      <div class="tabs" role="tablist" aria-label="音乐库分类">${tabs.map(([id, label]) => `<button class="tab" role="tab" aria-selected="${state.libraryTab === id}" data-action="library-tab" data-tab="${id}" id="tab-${id}" aria-controls="library-content">${label}</button>`).join('')}</div><section id="library-content" role="tabpanel" aria-labelledby="tab-${state.libraryTab}">${content}</section>`;
  }

  function settingsPage() {
    return `<header class="page-head"><div><p class="eyebrow">RxPlayer</p><h1>设置</h1><p class="page-intro">外观、音源与播放偏好。</p></div></header><div class="settings-columns"><section>
      <div class="section-head"><h2>外观</h2></div><div class="settings-group">
        <label class="settings-row"><span class="setting-icon">${symbol(state.theme === 'dark' ? 'dark_mode' : 'light_mode')}</span><span class="row-copy"><strong>主题</strong><small>选择舒服的明暗</small></span><select id="theme-select" aria-label="应用主题"><option value="light" ${state.theme === 'light' ? 'selected' : ''}>浅色</option><option value="dark" ${state.theme === 'dark' ? 'selected' : ''}>深色</option></select></label>
        <label class="settings-row"><span class="setting-icon">${symbol('album')}</span><span class="row-copy"><strong>封面舞台底色</strong><small>用封面取色衬托封面</small></span><span class="switch"><input type="checkbox" id="artwork-switch" aria-label="封面舞台底色" ${state.artworkColor ? 'checked' : ''}></span></label>
      </div><div class="section-head"><h2>播放</h2></div><div class="settings-group"><div class="settings-row"><span class="setting-icon">${symbol('history')}</span><span class="row-copy"><strong>恢复播放位置</strong><small>打开时保持暂停，由你决定何时继续</small></span>${symbol('check')}</div></div></section><section><div class="section-head"><h2>音源账号</h2><small>原型演示</small></div><div class="settings-group">${sources.map(source => `<div class="settings-row"><span class="setting-icon">${symbol('music_note')}</span><div class="row-copy"><strong>${source}</strong><small>${state.connected.includes(source) ? '<span class="account-tag">已连接</span> · 听风的人' : '未连接'}</small></div><button class="text-button" data-action="connect" data-source="${source}">${state.connected.includes(source) ? '断开' : '连接'}</button></div>`).join('')}</div><p class="hint">账号操作仅作界面演示，未连接任何平台。</p></section></div><p class="settings-signature">Rx.<span>让每一段声音，都有自己的位置。</span></p>`;
  }

  function queueItems() {
    return state.queue.map((id, index) => {
      const track = trackById(id);
      return `<div class="queue-item${id === state.current ? ' is-current' : ''}"><span class="queue-number" aria-hidden="true">${id === state.current ? symbol(state.playing ? 'graphic_eq' : 'music_note') : index + 1}</span><button class="now-track" data-action="play-track" data-id="${id}" aria-label="播放 ${escape(track.title)}" ${id === state.current ? 'aria-current="true"' : ''}>${art(track)}<span class="track-info"><span class="track-title">${escape(track.title)}</span><span class="track-sub">${track.source} · ${time(track.duration)}</span></span></button>${iconButton('remove-queue', 'close', `移除 ${track.title}`, `data-id="${id}"`)}</div>`;
    }).join('') || '<p class="queue-caption">队列还空着，把想听的内容加入进来。</p>';
  }

  function supportPane() {
    return `<header class="support-heading"><div><h2 id="queue-title">播放队列</h2><span class="queue-count">${state.queue.length} 首</span></div>${iconButton('close-queue','close','收起播放队列')}</header><div class="queue-list">${queueItems()}</div>`;
  }

  function queueDocked() {
    return state.device === 'desktop' && queueBreakpoint.matches;
  }

  function queueModal() {
    return state.queueOpen && !queueDocked();
  }

  function syncPanels() {
    const pane = $('#support-pane');
    const wasQueueOpen = product.dataset.queueOpen === 'true';
    const previousMode = product.dataset.queueMode;
    const modal = state.overlay && state.overlay !== 'player';
    product.dataset.queueOpen = state.queueOpen;
    product.dataset.queueDocked = state.queueOpen && queueDocked();
    product.dataset.queueMode = queueDocked() ? 'docked' : 'sheet';
    product.dataset.playerOpen = state.overlay === 'player';
    product.style.setProperty('--transport-height', `${$('#transport').getBoundingClientRect().height}px`);
    pane.hidden = !state.queueOpen;
    pane.setAttribute('role',queueDocked() ? 'complementary' : 'dialog');
    if (queueModal()) pane.setAttribute('aria-modal','true');
    else pane.removeAttribute('aria-modal');
    const lyrics = $('.desktop-lyrics');
    if (lyrics) lyrics.hidden = state.queueOpen && queueDocked();
    pane.inert = !!modal;
    $('#queue-scrim').hidden = !queueModal();
    $('#overlay').hidden = !state.overlay;
    $('#overlay').inert = queueModal();
    for (const element of [$('.workspace'),$('#transport'),$('#bottom-nav')]) element.inert = !!state.overlay || queueModal();
    $('#dialog').setAttribute('role',state.overlay === 'player' ? 'region' : 'dialog');
    if (modal) $('#dialog').setAttribute('aria-modal','true');
    else $('#dialog').removeAttribute('aria-modal');
    $$('[data-action="open-queue"]').forEach(button => {
      button.setAttribute('aria-expanded',String(state.queueOpen));
      button.setAttribute('aria-controls','support-pane');
      button.setAttribute('aria-label',`${state.queueOpen ? '收起' : '展开'}播放队列，${state.queue.length} 首`);
      if (button.dataset.tip) button.dataset.tip = state.queueOpen ? '收起播放队列' : '展开播放队列';
    });
    if (queueModal() && (!wasQueueOpen || previousMode !== 'sheet')) requestAnimationFrame(() => $('[data-action="close-queue"]',pane)?.focus());
  }

  function closeQueue(restoreFocus = true) {
    if (!state.queueOpen) return;
    state.queueOpen = false;
    syncPanels();
    if (!restoreFocus) return;
    const origin = queueFocusOrigin;
    const scope = origin?.region ? $(`#${origin.region}`) : product;
    const canFocus = element => element?.isConnected && element.getClientRects().length && !element.closest('[inert]');
    const replacement = origin?.key ? $$('[data-action]',scope).find(element => Object.entries(origin.key).every(([key,value]) => element.dataset[key] === value) && canFocus(element)) : null;
    const target = canFocus(origin?.element) ? origin.element : replacement;
    (target || $$(state.overlay === 'player' ? '#dialog [data-action="open-queue"]' : '#transport [data-action="open-queue"]').find(element => element.getClientRects().length))?.focus({preventScroll:true});
  }

  function toggleQueue() {
    if (state.queueOpen) { closeQueue(); return; }
    const active = document.activeElement;
    queueFocusOrigin = {element:active,key:active.dataset.action ? focusKey(active) : null,region:active.closest('#dialog,#transport,#main-pane,#rail,#bottom-nav')?.id};
    state.queueOpen = true;
    syncPanels();
    requestAnimationFrame(() => $('[data-action="close-queue"]',$('#support-pane'))?.focus());
  }

  function playButton(extra = '') {
    const icons = {preparing:'close',buffering:'pause',refreshing:'close',failed:'refresh',expired:'refresh'};
    const labels = {preparing:'取消准备',buffering:'暂停等待',refreshing:'取消获取地址',failed:'重试播放',expired:'重新获取播放地址',unavailable:'当前内容不可播放，查看提示'};
    const label = labels[state.phase] || (state.playing ? '暂停' : '播放');
    return `<button class="play-button ${state.playing ? 'is-playing' : ''} ${extra}" data-action="toggle-play" aria-label="${label}" data-tip="${label}">${symbol(icons[state.phase] || (state.playing ? 'pause' : 'play_arrow'), true)}</button>`;
  }

  function progress() {
    const known = state.prepared, position = known ? time(state.position) : '—:—', duration = known ? time(current().duration) : '—:—';
    const busy = waitingPhases.includes(state.phase);
    return `<div class="progress-line"><span data-position>${position}</span><span class="seek-track${busy ? ' is-buffering' : ''}"><input type="range" min="0" max="${known ? current().duration : 1}" value="${known ? state.position : 0}" style="--progress:${known ? state.position / current().duration * 100 : 0}%" aria-label="播放进度" aria-valuetext="${known ? `${position} / ${duration}` : '音频尚未就绪，总时长未知'}" data-progress ${canSeek() ? '' : 'disabled'}>${busy ? `<span class="buffer-track" role="progressbar" aria-label="${phaseLabels[state.phase]}"><span></span></span>` : ''}</span><span>${duration}</span></div>`;
  }

  function modeButton(action, icon, label, selected, tip = `${label}${selected ? '：已开启' : '：已关闭'}`) {
    return `<button class="icon-button mode-toggle" data-action="${action}" aria-label="${label}" data-tip="${tip}" aria-pressed="${selected}">${symbol(icon,selected)}</button>`;
  }

  const hasLyrics = () => state.lyricsLayout === 'auto' ? current().hasLyrics : state.lyricsLayout === 'available';

  function playerControls(desktop = false) {
    const shuffle = modeButton('shuffle','shuffle','随机播放',state.shuffle);
    const repeat = modeButton('repeat','repeat',state.repeat ? '列表循环' : '顺序播放',state.repeat,`当前${state.repeat ? '列表循环' : '顺序播放'}，点击切换`);
    if (!desktop && hasLyrics()) return `<div class="hero-controls controls-with-lyrics">${progress()}<div class="play-controls" role="group" aria-label="播放操作">${iconButton('previous','skip_previous','上一首')}${shuffle}${playButton()}${repeat}${iconButton('next','skip_next','下一首')}</div></div>`;
    const volume = desktop ? `<label class="player-volume">${symbol('volume_up')}<input class="volume" type="range" min="0" max="100" value="${state.volume}" style="--progress:${state.volume}%" aria-label="音量"></label>` : '';
    const modes = `<div class="player-secondary" role="group" aria-label="${desktop ? '播放模式与音量' : '播放模式'}">${shuffle}${repeat}${volume}</div>`;
    return `<div class="hero-controls controls-no-lyrics"><div class="split-controls" role="group" aria-label="播放操作">${playButton()}${iconButton('next','skip_next','下一首')}${iconButton('previous','skip_previous','上一首')}${progress()}</div>${modes}</div>`;
  }

  function seekTo(position) {
    if (!canSeek()) return;
    state.position = Math.max(0,Math.min(current().duration,position));
    $$('[data-position]').forEach(element => { element.textContent = time(state.position); });
    $$('[data-progress]').forEach(element => {
      element.value = state.position;
      element.style.setProperty('--progress', `${state.position / current().duration * 100}%`);
      element.setAttribute('aria-valuetext', `${time(state.position)} / ${time(current().duration)}`);
    });
    updateLyrics();
  }

  function lyricLines() {
    // The review toolbar can force the available state for a song without demo lyrics.
    const lines = demoLyrics[current().art].length ? demoLyrics[current().art] : demoLyrics[0];
    return lines.map((text,index) => ({text,at:Math.floor(index * current().duration / lines.length)}));
  }

  function activeLyricIndex() {
    if (!state.prepared) return -1;
    return lyricLines().findLastIndex(line => state.position >= line.at);
  }

  function lyricButtons(windowed = false) {
    const lines = lyricLines(), active = activeLyricIndex();
    const indices = windowed ? Array.from({length:5},(_,index) => Math.max(0,active) + index - 2) : lines.map((_,index) => index);
    return indices.map(index => {
      const line = lines[index];
      return line ? `<button class="lyric-line${index === active ? ' is-active' : ''}" data-action="seek-lyric" data-index="${index}" aria-label="跳转至 ${time(line.at)}，${escape(line.text)}" ${canSeek() ? '' : 'disabled'} ${index === active ? 'aria-current="true"' : ''}>${escape(line.text)}</button>` : '<span class="lyric-spacer" aria-hidden="true"></span>';
    }).join('');
  }

  function desktopLyrics() {
    return `<section class="desktop-lyrics" aria-labelledby="lyrics-title"><h3 id="lyrics-title">歌词</h3>${hasLyrics() ? `<div class="lyrics-lines">${lyricButtons()}</div>` : `<div class="lyrics-empty">${symbol('music_note')}<h4>暂无歌词</h4><p>这首歌暂未提供歌词</p></div>`}</section>`;
  }

  function updateLyrics() {
    const active = activeLyricIndex();
    const mobile = $('.mobile-lyric-lines');
    if (mobile && Number(mobile.dataset.active) !== active) {
      const focusedIndex = mobile.contains(document.activeElement) ? document.activeElement.dataset.index : null;
      mobile.innerHTML = lyricButtons(true);
      mobile.dataset.active = active;
      if (focusedIndex !== null) ($(`[data-index="${focusedIndex}"]`,mobile) || $('[aria-current="true"]',mobile))?.focus({preventScroll:true});
    }
    $$('#dialog .lyric-line').forEach(line => {
      const selected = Number(line.dataset.index) === active;
      line.classList.toggle('is-active',selected);
      if (selected) line.setAttribute('aria-current','true');
      else line.removeAttribute('aria-current');
    });
  }

  function transport() {
    const track = current();
    return `<button class="now-track" data-action="open-player" aria-label="展开 ${escape(track.title)} 的播放器">${art(track, 'mini')}<span class="track-info"><span class="track-title">${escape(track.title)}</span><span class="track-sub">${!state.prepared && state.phase === 'paused' ? '尚未播放' : phaseLabels[state.phase]} · ${track.source}</span></span></button>
      <div class="transport-center"><div class="play-controls">${iconButton('shuffle','shuffle','随机播放',`aria-pressed="${state.shuffle}"`)}${iconButton('previous','skip_previous','上一首')}${playButton()}${iconButton('next','skip_next','下一首')}${iconButton('repeat','repeat','列表循环',`aria-pressed="${state.repeat}"`)}</div>${progress()}</div>
      <div class="transport-right"><span class="symbol volume-icon" aria-hidden="true">volume_up</span><input class="volume" type="range" min="0" max="100" value="${state.volume}" style="--progress:${state.volume}%" aria-label="音量">${iconButton('open-queue','queue_music','播放队列')}</div>
      ${playButton('mini-play')}<button class="icon-button mini-queue" data-action="open-queue" aria-label="播放队列">${symbol('queue_music')}</button>`;
  }

  function render() {
    const active = document.activeElement;
    const searchFocus = active.id === 'search-input' ? {start:active.selectionStart,end:active.selectionEnd} : null;
    const queueFocus = $('#support-pane').contains(active) ? focusKey(active) : null;
    const focusedData = product.contains(active) && active.dataset.action && !state.overlay ? focusKey(active) : null;
    product.dataset.device = state.device;
    product.dataset.theme = state.theme;
    product.dataset.playing = state.playing;
    product.dataset.playbackPhase = state.phase;
    product.dataset.prepared = state.prepared;
    product.dataset.lyricsMode = hasLyrics() ? 'available' : 'none';
    const palette = globalThis.rxCoverPalettes.covers[current().art];
    product.dataset.coverArt = palette.art;
    Object.entries(palette[state.theme]).forEach(([role,color]) => product.style.setProperty(`--cover-${role}`,color));
    product.style.setProperty('--lyrics-primary',palette.dark.primary);
    product.classList.remove('is-searching');
    $('#rail').innerHTML = '<div class="brand" aria-label="RxPlayer">Rx.</div>' + nav();
    $('#bottom-nav').innerHTML = nav();
    $('#main-pane').innerHTML = state.route === 'search' ? searchPage() : state.route === 'library' ? libraryPage() : settingsPage();
    $('#support-pane').innerHTML = supportPane();
    $('#transport').innerHTML = transport();
    $('#scene-select').value = state.scene;
    $('#lyrics-layout-select').value = state.lyricsLayout;
    $('#playback-select').value = state.phase;
    $('#review-complete').hidden = !waitingPhases.includes(state.phase) && state.scene !== 'loading';
    $$('.device-picker button').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.deviceChoice === state.device)));
    $('#review-theme').innerHTML = symbol(state.theme === 'light' ? 'dark_mode' : 'light_mode');
    if (state.overlay) renderOverlay();
    else if (focusedData) {
      $$('[data-action]', product).find(element => Object.entries(focusedData).every(([key,value]) => element.dataset[key] === value) && element.getClientRects().length)?.focus({preventScroll:true});
    }
    syncPanels();
    if (searchFocus && !state.overlay && !queueModal()) {
      $('#search-input')?.focus({preventScroll:true});
      $('#search-input')?.setSelectionRange(searchFocus.start,searchFocus.end);
    }
    if (queueFocus && state.queueOpen) ($$('[data-action]',$('#support-pane')).find(element => Object.entries(queueFocus).every(([key,value]) => element.dataset[key] === value)) || $('[data-action="close-queue"]',$('#support-pane')))?.focus({preventScroll:true});
  }

  function toast(message) {
    const snackbar = $('#snackbar');
    clearTimeout(toastTimer);
    snackbar.textContent = message;
    snackbar.hidden = false;
    toastTimer = setTimeout(() => { snackbar.hidden = true; }, 3000);
  }

  function openOverlay(type, trackId) {
    if (queueModal()) closeQueue(false);
    if (type === 'player') state.queueOpen = false;
    const entering = !state.overlay;
    previousFocus = document.activeElement;
    previousFocusData = previousFocus.dataset.action ? focusKey(previousFocus) : null;
    previousFocusRegion = previousFocus.closest('#transport,#main-pane,#rail,#bottom-nav')?.id;
    state.overlay = type;
    if (trackId) state.menuTrack = trackId;
    renderOverlay();
    syncPanels();
    if (entering && !matchMedia('(prefers-reduced-motion:reduce)').matches) {
      $('#dialog').animate([{transform:'translateY(32px) scale(.97)'},{transform:'translateY(0) scale(1)'}], {duration:500,easing:'cubic-bezier(.38,1.21,.22,1)'});
      $('#dialog').animate([{opacity:0},{opacity:1}], {duration:200,easing:'cubic-bezier(.34,.8,.34,1)'});
    }
    requestAnimationFrame(() => $('#dialog input, #dialog button')?.focus());
  }

  function closeOverlay() {
    if (!state.overlay) return;
    if (queueModal()) closeQueue(false);
    state.overlay = null;
    syncPanels();
    const replacement = previousFocusData ? $$('[data-action]', previousFocusRegion ? $(`#${previousFocusRegion}`) : product).find(element => Object.entries(previousFocusData).every(([key,value]) => element.dataset[key] === value) && element.getClientRects().length) : null;
    if (previousFocus?.isConnected && previousFocus.getClientRects().length) previousFocus.focus({preventScroll:true});
    else if (replacement) replacement.focus({preventScroll:true});
    else $$('#main-pane button, #transport button').find(element => element.getClientRects().length > 0)?.focus({preventScroll:true});
  }

  function renderOverlay() {
    const dialog = $('#dialog');
    const wasInDialog = dialog.contains(document.activeElement);
    const focusedData = wasInDialog ? focusKey(document.activeElement) : null;
    dialog.className = `sheet ${state.overlay === 'player' ? `full-player${state.device === 'desktop' ? ' desktop-player' : hasLyrics() ? ' immersive-player' : ''}${state.artworkColor ? ' artwork-tone' : ''}` : ''}`;
    let title = '';
    let body = '';
    if (state.overlay === 'player') {
      const track = current();
      title = '播放器';
      const desktop = state.device === 'desktop';
      const artwork = `<div class="player-art-column"><div class="art-stage">${art(track, 'large')}</div></div>`;
      const detail = `<div class="player-detail"><span class="source-label">${track.source} · ${track.type}</span><div class="player-title-row"><div><h3 class="player-title">${escape(track.title)}</h3><p class="player-sub">${escape(track.author)}</p></div>${favoriteButton(track)}</div>${playerControls(desktop)}</div>`;
      if (desktop) body = `<div class="desktop-listening-layout"><section class="desktop-player-column" aria-label="当前播放控制">${artwork}${detail}</section>${desktopLyrics()}</div>`;
      else if (hasLyrics()) body = `<div class="mobile-lyrics-player"><section class="mobile-lyrics-stage" aria-labelledby="mobile-lyrics-title"><div class="lyrics-backdrop" aria-hidden="true">${art(track,'lyrics-background')}</div><h3 id="mobile-lyrics-title" class="sr-only">歌词</h3><div class="mobile-lyric-lines" data-active="${activeLyricIndex()}">${lyricButtons(true)}</div></section><div class="mobile-player-meta"><div><h3 class="mobile-track-title">${escape(track.title)}</h3><p>${escape(track.author)} · ${track.source}</p></div><div class="mobile-track-actions">${iconButton('open-queue','queue_music',`播放队列，${state.queue.length} 首`)}${favoriteButton(track)}</div></div>${playerControls()}</div>`;
      else body = `<div class="player-layout">${artwork}${detail}</div>`;
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
    const heading = state.overlay === 'player'
      ? `<header class="sheet-head player-head">${iconButton('close-overlay','arrow_back','返回')}<h2 id="dialog-title" class="sr-only">播放器</h2>${state.device === 'android' && hasLyrics() ? '' : `<button class="queue-pill" data-action="open-queue" aria-label="播放队列，${state.queue.length} 首">${symbol('queue_music')}<span>队列 · ${state.queue.length}</span></button>`}</header>`
      : `<header class="sheet-head"><h2 id="dialog-title">${escape(title)}</h2>${iconButton('close-overlay','close','关闭')}</header>`;
    dialog.innerHTML = `${heading}${body}`;
    if (wasInDialog) requestAnimationFrame(() => {
      const target = focusedData ? $$('[data-action]',dialog).find(element => !element.disabled && Object.entries(focusedData).every(([key,value]) => element.dataset[key] === value)) : null;
      (target || $('[data-action="toggle-play"]:not(:disabled)',dialog) || $('button:not(:disabled),input:not(:disabled),select', dialog))?.focus({preventScroll:true});
    });
  }

  function playTrack(id) {
    cancelPlayback();
    state.current = id;
    state.position = 0;
    state.prepared = false;
    if (!state.queue.includes(id)) state.queue.push(id);
    preparePlayback('preparing',true);
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
    if (deviceChoice) { closeQueue(false); closeOverlay(); state.device = deviceChoice.dataset.deviceChoice; state.queueOpen = queueDocked(); render(); return; }
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const {action, id, source} = button.dataset;
    switch (action) {
      case 'navigate': closeOverlay(); state.route = button.dataset.route; state.playlist = null; render(); break;
      case 'source': cancelSearch(); state.queries[state.source] = $('#search-input').value; state.source = source; state.scene = 'results'; render(); break;
      case 'go-search': state.route = 'search'; render(); break;
      case 'history': state.queries[state.source] = button.dataset.query; runSearch(); break;
      case 'clear-history': state.history = []; render(); break;
      case 'clear-search': cancelSearch(); state.queries[state.source] = ''; state.scene = 'idle'; render(); $('#search-input')?.focus(); break;
      case 'cancel-search': cancelSearch(); state.scene = 'idle'; render(); $('#search-input')?.focus(); announce('已取消搜索，当前播放未改变'); break;
      case 'exit-search': product.classList.remove('is-searching'); $('#search-back').hidden = true; $('#search-leading').hidden = false; $('#search-input').blur(); break;
      case 'retry': runSearch(); break;
      case 'play-track': playTrack(id); break;
      case 'toggle-play': togglePlayback(); break;
      case 'next': step(1); break;
      case 'previous': step(-1); break;
      case 'seek-lyric': seekTo(lyricLines()[Number(button.dataset.index)].at); break;
      case 'favorite': state.favorites = state.favorites.includes(id) ? state.favorites.filter(item => item !== id) : [...state.favorites, id]; render(); toast(state.favorites.includes(id) ? '已收藏' : '已取消收藏'); break;
      case 'track-menu': openOverlay('menu', id); break;
      case 'open-player': openOverlay('player'); break;
      case 'open-queue': toggleQueue(); break;
      case 'close-queue': closeQueue(); break;
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
        if (id === state.current) {
          cancelPlayback(); state.wantPlay = false; state.prepared = false; setPhase('paused');
          if (state.queue.length) { state.current = state.queue[0]; state.position = 0; }
        }
        render(); break;
      }
      case 'shuffle': state.shuffle = !state.shuffle; render(); toast(state.shuffle ? '已开启随机播放' : '已关闭随机播放'); break;
      case 'repeat': state.repeat = !state.repeat; render(); toast(state.repeat ? '已切换为列表循环' : '已切换为顺序播放'); break;
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
        if (query) state.history = [query, ...state.history.filter(item => item !== query)].slice(0, 6);
        if (query) runSearch();
        else { cancelSearch(); state.scene = 'idle'; render(); }
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
    if (event.target.id === 'scene-select') { cancelSearch(); closeQueue(false); closeOverlay(); state.route = 'search'; state.scene = event.target.value; render(); announce(state.scene === 'loading' ? '正在搜索，审核场景保持等待' : '搜索场景已切换，当前播放不变'); }
    if (event.target.id === 'playback-select') {
      previewPhase(event.target.value); render();
      if (state.overlay !== 'player') openOverlay('player');
      announce(phaseLabels[state.phase]);
    }
    if (event.target.id === 'theme-select') { state.theme = event.target.value; render(); }
    if (event.target.id === 'lyrics-layout-select') { state.lyricsLayout = event.target.value; render(); }
    if (event.target.id === 'artwork-switch') { state.artworkColor = event.target.checked; if (state.overlay === 'player') renderOverlay(); toast('播放页配色偏好已更新'); }
  });

  document.addEventListener('input', event => {
    if (event.target.id === 'search-input') {
      state.queries[state.source] = event.target.value;
      if (state.scene === 'loading') { cancelSearch(); state.scene = 'idle'; render(); }
    }
    if (event.target.matches('.volume')) {
      state.volume = Number(event.target.value);
      $$('.volume').forEach(input => { input.value = state.volume; input.style.setProperty('--progress', `${state.volume}%`); });
    }
    if (event.target.matches('[data-progress]')) {
      seekTo(Number(event.target.value));
    }
  });

  document.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      if (state.queueOpen && (queueModal() || $('#support-pane').contains(document.activeElement))) { event.preventDefault(); closeQueue(); }
      else if (state.overlay) { event.preventDefault(); closeOverlay(); }
      else if (document.activeElement?.id === 'search-input') { event.preventDefault(); $('[data-action="exit-search"]').click(); }
      return;
    }
    if (event.key === 'Tab' && (queueModal() || state.overlay && state.overlay !== 'player')) {
      const controls = $$('button,input,select,a[href]', queueModal() ? $('#support-pane') : $('#dialog')).filter(element => !element.disabled && element.getClientRects().length);
      const first = controls[0], last = controls.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      return;
    }
    const input = document.activeElement?.matches('input,textarea,select,[contenteditable=true]');
    const focusedButton = document.activeElement?.tagName === 'BUTTON';
    if (event.code === 'Space' && !input && !focusedButton && (!state.overlay || state.overlay === 'player') && !queueModal()) { event.preventDefault(); togglePlayback(); }
    if (['ArrowLeft','ArrowRight'].includes(event.key) && event.target instanceof Element && event.target.closest('.sources')) {
      event.preventDefault();
      cancelSearch(); state.scene = 'results';
      state.source = sources[(sources.indexOf(state.source) + (event.key === 'ArrowRight' ? 1 : 2)) % 3];
      render(); $(`.source-button[data-source="${state.source}"]`).focus();
    }
  });

  $('#review-theme').addEventListener('click', () => { state.theme = state.theme === 'light' ? 'dark' : 'light'; render(); });
  $('#review-player').addEventListener('click', () => openOverlay('player'));
  $('#review-complete').addEventListener('click', () => {
    if (state.overlay === 'player' && waitingPhases.includes(state.phase)) finishPlayback();
    else if (state.scene === 'loading') finishSearch();
    else if (waitingPhases.includes(state.phase)) finishPlayback();
    requestAnimationFrame(() => (state.overlay === 'player' ? $('#dialog [data-action="toggle-play"]:not(:disabled)') : $('#search-input'))?.focus({preventScroll:true}));
  });
  $('#review-reset').addEventListener('click', () => { cancelSearch(); cancelPlayback(); closeQueue(false); closeOverlay(); queueFocusOrigin = null; state = initial(); render(); toast('已恢复原型初始状态'); });
  new ResizeObserver(syncPanels).observe(product);
  queueBreakpoint.addEventListener('change',syncPanels);
  setInterval(() => {
    if (!state.playing) return;
    if (state.position >= current().duration) {
      const index = state.queue.indexOf(state.current);
      if (state.repeat || index < state.queue.length - 1) step(1);
      else { state.wantPlay = false; setPhase('ended'); render(); announce('当前队列已播放完'); }
      return;
    }
    state.position += 1;
    updateLyrics();
    $$('[data-position]').forEach(element => { element.textContent = time(state.position); });
    $$('[data-progress]').filter(element => element !== document.activeElement).forEach(element => {
      element.value = state.position;
      element.style.setProperty('--progress', `${state.position / current().duration * 100}%`);
      element.setAttribute('aria-valuetext', `${time(state.position)} / ${time(current().duration)}`);
    });
  }, 1000);
  const review = new URLSearchParams(location.search);
  if (['android','desktop'].includes(review.get('device'))) state.device = review.get('device');
  if (['dark','light'].includes(review.get('theme'))) state.theme = review.get('theme');
  if (['results','idle','loading','empty','error','expired'].includes(review.get('scene'))) state.scene = review.get('scene');
  if (Object.hasOwn(phaseLabels,review.get('playback'))) previewPhase(review.get('playback'));
  render();
  if (review.get('view') === 'player') openOverlay('player');
})();
