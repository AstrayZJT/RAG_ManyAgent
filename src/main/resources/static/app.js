const els = {
  taskQuery: document.getElementById('task-query'),
  taskLanguage: document.getElementById('task-language'),
  queryPresets: document.getElementById('query-presets'),
  createTaskBtn: document.getElementById('create-task-btn'),
  runCurrentBtn: document.getElementById('run-current-btn'),
  documentDropzone: document.getElementById('document-dropzone'),
  documentFile: document.getElementById('document-file'),
  chooseFileBtn: document.getElementById('choose-file-btn'),
  uploadDocumentBtn: document.getElementById('upload-document-btn'),
  indexDocumentBtn: document.getElementById('index-document-btn'),
  refreshDocumentsBtn: document.getElementById('refresh-documents-btn'),
  documentList: document.getElementById('document-list'),
  refreshTasksBtn: document.getElementById('refresh-tasks-btn'),
  taskList: document.getElementById('task-list'),
  currentTaskTitle: document.getElementById('current-task-title'),
  currentTaskSubtitle: document.getElementById('current-task-subtitle'),
  streamToggle: document.getElementById('stream-toggle'),
  refreshCurrentBtn: document.getElementById('refresh-current-btn'),
  runSelectedBtn: document.getElementById('run-selected-btn'),
  statusValue: document.getElementById('status-value'),
  evidenceValue: document.getElementById('evidence-value'),
  checkpointValue: document.getElementById('checkpoint-value'),
  evaluationValue: document.getElementById('evaluation-value'),
  currentChips: document.getElementById('current-chips'),
  workflowRail: document.getElementById('workflow-rail'),
  tabbar: document.getElementById('tabbar'),
  tabOverview: document.getElementById('tab-overview'),
  tabTimeline: document.getElementById('tab-timeline'),
  tabReport: document.getElementById('tab-report'),
  tabCheckpoints: document.getElementById('tab-checkpoints'),
  tabEvaluation: document.getElementById('tab-evaluation'),
  streamStatus: document.getElementById('stream-status'),
  liveFeed: document.getElementById('live-feed'),
  resumeCheckpoint: document.getElementById('resume-checkpoint'),
  rerunNode: document.getElementById('rerun-node'),
  beforeNode: document.getElementById('before-node'),
  statePatch: document.getElementById('state-patch'),
  resumeBtn: document.getElementById('resume-btn'),
  rerunBtn: document.getElementById('rerun-btn'),
  previewBeforeBtn: document.getElementById('preview-before-btn'),
  evaluateBtn: document.getElementById('evaluate-btn'),
  recoveryPreview: document.getElementById('recovery-preview'),
  toastHost: document.getElementById('toast-host')
};

const STORAGE_KEYS = {
  selectedTaskId: 'insightflow:selectedTaskId',
  selectedDocumentId: 'insightflow:selectedDocumentId',
  activeTab: 'insightflow:activeTab',
  liveEnabled: 'insightflow:liveEnabled',
  queryText: 'insightflow:queryText',
  language: 'insightflow:language',
  beforeNode: 'insightflow:beforeNode',
  rerunNode: 'insightflow:rerunNode',
  resumeCheckpointId: 'insightflow:resumeCheckpointId',
  statePatch: 'insightflow:statePatch'
};

const DEFAULT_QUERY = '请分析比亚迪、特斯拉与理想汽车在中国新能源车市场的竞争格局，并给出证据链与引用。';
const DEFAULT_PATCH = '{}';

const QUERY_PRESETS = [
  {
    label: '新能源车竞品',
    language: 'zh-CN',
    query: '请分析比亚迪、特斯拉与理想汽车在中国新能源车市场的竞争格局，并给出证据链与引用。'
  },
  {
    label: '企业 AI 平台',
    language: 'en-US',
    query: 'Compare OpenAI, Anthropic, and Google for enterprise AI platform adoption, pricing, and distribution advantages.'
  },
  {
    label: '供应链变量',
    language: 'zh-CN',
    query: '梳理新能源车、储能和智能驾驶三个赛道的供应链关键变量与风险点。'
  },
  {
    label: '半导体设备',
    language: 'zh-CN',
    query: '分析半导体设备国产替代的核心环节、壁垒与近期验证信号。'
  }
];

const BEFORE_NODE_OPTIONS = [
  { value: '', label: '未指定' },
  { value: 'planner', label: 'Planner 之前' },
  { value: 'retrievalStart', label: 'Retrieval 分发前' },
  { value: 'retrieveInternal', label: '内部检索前' },
  { value: 'retrieveExternal', label: '外部检索前' },
  { value: 'mergeRerank', label: '合并重排前' },
  { value: 'extract', label: '抽取前' },
  { value: 'verify', label: '验证前' },
  { value: 'write', label: '写作前' },
  { value: 'review', label: '审查前' }
];

const RERUN_NODE_OPTIONS = [
  { value: 'retrievalStart', label: '检索调度' },
  { value: 'retrieveInternal', label: '内部检索' },
  { value: 'retrieveExternal', label: '外部检索' },
  { value: 'mergeRerank', label: '合并与重排' },
  { value: 'extract', label: '事实抽取' },
  { value: 'verify', label: '交叉验证' },
  { value: 'write', label: '生成报告' },
  { value: 'review', label: '报告审查' }
];

const WORKFLOW_STAGES = [
  { key: 'task', label: '任务', fallback: '研究任务生命周期' },
  { key: 'planner', label: '规划器', fallback: '将研究目标拆成维度与子查询' },
  { key: 'retrievalStart', label: '检索', fallback: '决定使用内部还是外部来源' },
  { key: 'retrieveInternal', label: '内部检索', fallback: '拉取知识库证据' },
  { key: 'retrieveExternal', label: '外部检索', fallback: '抓取并清洗网页证据' },
  { key: 'mergeRerank', label: '合并重排', fallback: '合并、去重并重排证据' },
  { key: 'extract', label: '抽取', fallback: '抽取结构化事实' },
  { key: 'verify', label: '验证', fallback: '交叉核验结论与置信度' },
  { key: 'write', label: '写作', fallback: '生成报告草稿' },
  { key: 'review', label: '审查', fallback: '检查证据缺口并决定回退' }
];

const STREAM_STAGES = Array.from(new Set([
  ...WORKFLOW_STAGES.map((stage) => stage.key),
  'checkpoint',
  'stream'
]));

const state = {
  tasks: [],
  documents: [],
  selectedTaskId: readStorage(STORAGE_KEYS.selectedTaskId, ''),
  selectedDocumentId: readStorage(STORAGE_KEYS.selectedDocumentId, ''),
  activeTab: readStorage(STORAGE_KEYS.activeTab, 'overview'),
  liveEnabled: readStorage(STORAGE_KEYS.liveEnabled, 'true') !== 'false',
  queryText: readStorage(STORAGE_KEYS.queryText, DEFAULT_QUERY) || DEFAULT_QUERY,
  language: readStorage(STORAGE_KEYS.language, 'zh-CN') || 'zh-CN',
  beforeNode: readStorage(STORAGE_KEYS.beforeNode, ''),
  rerunNode: readStorage(STORAGE_KEYS.rerunNode, 'verify') || 'verify',
  resumeCheckpointId: readStorage(STORAGE_KEYS.resumeCheckpointId, ''),
  statePatchText: readStorage(STORAGE_KEYS.statePatch, DEFAULT_PATCH) || DEFAULT_PATCH,
  loadingTask: false,
  taskDetail: null,
  timeline: null,
  report: null,
  previewMode: '',
  recoveryPreview: null,
  recoveryPreviewLabel: '',
  liveEvents: [],
  liveEventKeys: new Set(),
  liveSource: null,
  liveSourceTaskId: '',
  refreshTimer: null,
  reconnectTimer: null
};

let taskLoadSeq = 0;
let previewLoadSeq = 0;
let taskListLoadSeq = 0;
let documentListLoadSeq = 0;

function readStorage(key, fallback) {
  try {
    return localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}

function writeStorage(key, value) {
  try {
    if (value === undefined || value === null || value === '') {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, String(value));
    }
  } catch {
    // ignore
  }
}

function qs(selector, root = document) {
  return root.querySelector(selector);
}

function qsa(selector, root = document) {
  return Array.from(root.querySelectorAll(selector));
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function titleCase(value) {
  return String(value ?? '')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function prettyLabel(value) {
  return titleCase(value);
}

function shortId(value) {
  const text = String(value ?? '');
  return text.length > 10 ? `${text.slice(0, 8)}…` : text;
}

function truncate(value, max = 120) {
  const text = String(value ?? '');
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

function formatDate(value, options = {}) {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
    ...options
  }).format(date);
}

function formatNumber(value, digits = 0) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '—';
  }
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  }).format(numeric);
}

function normalizePercent(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return null;
  }
  return numeric <= 1 ? numeric * 100 : numeric;
}

function formatPercent(value, digits = 0) {
  const percent = normalizePercent(value);
  if (percent === null) {
    return '—';
  }
  return `${percent.toFixed(digits)}%`;
}

function formatDuration(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return '—';
  }
  if (numeric >= 1000) {
    return `${(numeric / 1000).toFixed(numeric >= 10000 ? 0 : 1)}s`;
  }
  return `${Math.round(numeric)}ms`;
}

function formatValue(value, limit = 120) {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否';
  }
  if (typeof value === 'number') {
    return formatNumber(value);
  }
  if (Array.isArray(value)) {
    if (!value.length) {
      return '[]';
    }
    return truncate(value.map((item) => formatValue(item, 40)).join(', '), limit);
  }
  if (typeof value === 'object') {
    return truncate(JSON.stringify(value), limit);
  }
  return truncate(String(value), limit);
}

function slugifyStatus(value) {
  return String(value ?? 'unknown').toLowerCase().replace(/[^a-z0-9]+/g, '-');
}

function statusTone(value) {
  const normalized = String(value ?? '').toUpperCase();
  if (['COMPLETED', 'INDEXED', 'APPROVED', 'CONNECTED', 'SAVED'].includes(normalized)) {
    return 'success';
  }
  if (['RUNNING', 'INDEXING', 'ACCEPTED', 'UPDATED'].includes(normalized)) {
    return 'info';
  }
  if (['FAILED', 'REJECTED', 'ERROR'].includes(normalized)) {
    return 'danger';
  }
  if (['WARNING', 'PARTIAL', 'LOW_CONFIDENCE', 'INSUFFICIENT'].includes(normalized)) {
    return 'warning';
  }
  return 'muted';
}

function statusLabel(value) {
  if (value === null || value === undefined || value === '') {
    return '未知';
  }
  const normalized = String(value).toUpperCase();
  const map = {
    UNKNOWN: '未知',
    IDLE: '待机',
    CREATED: '已创建',
    PENDING: '待处理',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
    INDEXED: '已索引',
    INDEXING: '索引中',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CONNECTED: '已连接',
    PAUSED: '已暂停',
    DISCONNECTED: '未连接',
    CONNECTING: '连接中',
    ACCEPTED: '已受理',
    UPDATED: '已更新',
    WARNING: '警告',
    PARTIAL: '部分完成',
    LOW_CONFIDENCE: '低置信度',
    INSUFFICIENT: '证据不足',
    SAVED: '已保存',
    ERROR: '错误'
  };
  return map[normalized] || titleCase(value);
}

function humanizeStage(value) {
  const map = {
    task: '任务',
    planner: '规划器',
    retrievalStart: '检索调度',
    retrieveInternal: '内部检索',
    retrieveExternal: '外部检索',
    mergeRerank: '合并重排',
    extract: '事实抽取',
    verify: '交叉验证',
    write: '生成报告',
    review: '报告审查',
    checkpoint: '检查点',
    stream: '实时流'
  };
  return map[value] || prettyLabel(value);
}

function humanizeSaveMode(value) {
  const map = {
    snapshot: '快照',
    manual: '手动',
    auto: '自动',
    recovery: '恢复'
  };
  return map[String(value || '').toLowerCase()] || prettyLabel(value);
}

function escapeAttribute(value) {
  return escapeHtml(value).replace(/`/g, '&#96;');
}

function safeObject(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
}

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function getTaskSnapshot(taskId = state.selectedTaskId) {
  const detail = state.taskDetail && state.taskDetail.id === taskId ? state.taskDetail : null;
  const summary = state.tasks.find((task) => task.id === taskId) || null;
  return detail || summary || null;
}

function getSelectedTaskId() {
  return state.selectedTaskId || '';
}

function getSelectedDocument() {
  return state.documents.find((document) => document.id === state.selectedDocumentId) || null;
}

function renderPill(text, tone = 'muted') {
  return `<span class="pill ${tone}">${escapeHtml(text)}</span>`;
}

function renderChip(text, tone = '') {
  return `<span class="chip ${tone}">${escapeHtml(text)}</span>`;
}

function renderKeyValueGrid(source, emptyLabel = '暂无详情') {
  const entries = Object.entries(safeObject(source));
  if (!entries.length) {
    return `<div class="empty-state"><h3>${escapeHtml(emptyLabel)}</h3><p>这里暂时没有可查看的内容。</p></div>`;
  }
  return `<div class="kv-grid">${entries.map(([key, value]) => `
    <div class="kv-card">
      <span>${escapeHtml(prettyLabel(key))}</span>
      <strong>${escapeHtml(formatValue(value))}</strong>
    </div>
  `).join('')}</div>`;
}

function renderMetricCard({ label, value, subtitle = '', ratio = false }) {
  const numeric = ratio ? normalizePercent(value) : null;
  const bar = ratio && numeric !== null ? `<div class="bar"><div class="bar-fill" style="width:${Math.max(0, Math.min(100, numeric))}%"></div></div>` : '';
  const valueHtml = ratio
    ? (numeric === null ? '—' : `${Math.round(numeric)}%`)
    : escapeHtml(formatValue(value));
  return `
    <article class="metric-card">
      <div class="metric-label">${escapeHtml(label)}</div>
      <div class="metric-value">${valueHtml}</div>
      ${subtitle ? `<div class="metric-subtitle">${escapeHtml(subtitle)}</div>` : ''}
      ${bar}
    </article>
  `;
}

function renderSectionCard({ title, subtitle = '', badge = '', body = '', className = '' }) {
  return `
    <article class="summary-card ${className}">
      <div class="summary-head">
        <div>
          <h3>${escapeHtml(title)}</h3>
          ${subtitle ? `<p>${escapeHtml(subtitle)}</p>` : ''}
        </div>
        ${badge}
      </div>
      ${body}
    </article>
  `;
}

function renderEmptyState(title, body, chips = '') {
  return `
    <div class="empty-state">
      <h3>${escapeHtml(title)}</h3>
      <p>${escapeHtml(body)}</p>
      ${chips}
    </div>
  `;
}

function renderMarkdown(markdown) {
  if (!markdown) {
    return '';
  }
  const lines = String(markdown).replace(/\r\n/g, '\n').split('\n');
  let html = '';
  let paragraph = [];
  let inUl = false;
  let inOl = false;
  let inCode = false;
  let codeLanguage = '';
  let codeLines = [];

  const inline = (text) => {
    let output = escapeHtml(text);
    output = output.replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, (_, label, url) => {
      return `<a href="${escapeAttribute(url)}" target="_blank" rel="noopener noreferrer">${label}</a>`;
    });
    output = output.replace(/`([^`]+)`/g, '<code>$1</code>');
    output = output.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    output = output.replace(/\*([^*]+)\*/g, '<em>$1</em>');
    return output;
  };

  const endLists = () => {
    if (inUl) {
      html += '</ul>';
      inUl = false;
    }
    if (inOl) {
      html += '</ol>';
      inOl = false;
    }
  };

  const flushParagraph = () => {
    if (!paragraph.length) {
      return;
    }
    endLists();
    html += `<p>${inline(paragraph.join(' '))}</p>`;
    paragraph = [];
  };

  const flushCode = () => {
    const content = escapeHtml(codeLines.join('\n'));
    const lang = codeLanguage ? ` data-lang="${escapeAttribute(codeLanguage)}"` : '';
    html += `<pre><code${lang}>${content}</code></pre>`;
    codeLines = [];
    codeLanguage = '';
  };

  for (const rawLine of lines) {
    const line = rawLine.replace(/\s+$/, '');
    const trimmed = line.trim();

    if (trimmed.startsWith('```')) {
      if (inCode) {
        flushCode();
        inCode = false;
      } else {
        flushParagraph();
        endLists();
        inCode = true;
        codeLanguage = trimmed.slice(3).trim();
      }
      continue;
    }

    if (inCode) {
      codeLines.push(line);
      continue;
    }

    if (!trimmed) {
      flushParagraph();
      endLists();
      continue;
    }

    const heading = trimmed.match(/^(#{1,6})\s+(.*)$/);
    if (heading) {
      flushParagraph();
      endLists();
      html += `<h${heading[1].length}>${inline(heading[2])}</h${heading[1].length}>`;
      continue;
    }

    const quote = trimmed.match(/^>\s?(.*)$/);
    if (quote) {
      flushParagraph();
      endLists();
      html += `<blockquote>${inline(quote[1])}</blockquote>`;
      continue;
    }

    const unordered = trimmed.match(/^[-*]\s+(.*)$/);
    if (unordered) {
      flushParagraph();
      if (inOl) {
        html += '</ol>';
        inOl = false;
      }
      if (!inUl) {
        html += '<ul>';
        inUl = true;
      }
      html += `<li>${inline(unordered[1])}</li>`;
      continue;
    }

    const ordered = trimmed.match(/^\d+\.\s+(.*)$/);
    if (ordered) {
      flushParagraph();
      if (inUl) {
        html += '</ul>';
        inUl = false;
      }
      if (!inOl) {
        html += '<ol>';
        inOl = true;
      }
      html += `<li>${inline(ordered[1])}</li>`;
      continue;
    }

    paragraph.push(trimmed);
  }

  flushParagraph();
  endLists();
  if (inCode) {
    flushCode();
  }
  return html;
}

function renderChips(values, tone = '') {
  const items = safeArray(values);
  if (!items.length) {
    return '';
  }
  return items.map((value) => renderChip(value, tone)).join('');
}

function sourceTypeLabel(value) {
  const map = {
    INTERNAL: '知识库',
    EXTERNAL: '网页'
  };
  return map[String(value || '').toUpperCase()] || '来源';
}

function renderCitationItem(citation, fallbackId = '') {
  if (!citation) {
    return `
      <div class="citation-item is-missing">
        <div class="citation-title">未找到引用详情</div>
        <div class="citation-meta">${escapeHtml(fallbackId)}</div>
      </div>
    `;
  }

  const source = sourceTypeLabel(citation.sourceType);
  const title = citation.title || citation.id || '未命名来源';
  const score = Number.isFinite(Number(citation.score)) ? `相关度 ${formatNumber(citation.score, 2)}` : '';
  const meta = [source, score, citation.documentId ? `文档 ${shortId(citation.documentId)}` : '', citation.chunkId ? `片段 ${shortId(citation.chunkId)}` : '']
    .filter(Boolean)
    .join(' · ');
  const link = citation.url
    ? `<a class="citation-link" href="${escapeAttribute(citation.url)}" target="_blank" rel="noopener noreferrer">打开来源</a>`
    : '';

  return `
    <div class="citation-item">
      <div class="citation-item-head">
        <div>
          <div class="citation-title">${escapeHtml(title)}</div>
          <div class="citation-meta">${escapeHtml(meta || citation.id || '引用来源')}</div>
        </div>
        ${link}
      </div>
      ${citation.snippet ? `<p class="citation-snippet">${escapeHtml(citation.snippet)}</p>` : ''}
    </div>
  `;
}

function renderSectionCitations(evidenceIds, citationIndex) {
  const ids = safeArray(evidenceIds).filter(Boolean);
  if (!ids.length) {
    return '';
  }
  return `
    <div class="citation-list">
      <div class="citation-list-label">本节引用</div>
      ${ids.map((id) => renderCitationItem(citationIndex.get(String(id)), id)).join('')}
    </div>
  `;
}

function renderPairs(source, emptyLabel = '暂无数据') {
  const entries = Object.entries(safeObject(source));
  if (!entries.length) {
    return `<div class="empty-state"><h3>${escapeHtml(emptyLabel)}</h3><p>这里暂时没有捕获到数据。</p></div>`;
  }
  return `<div class="kv-grid">${entries.map(([key, value]) => `
    <div class="kv-card">
      <span>${escapeHtml(prettyLabel(key))}</span>
      <strong>${escapeHtml(formatValue(value, 160))}</strong>
    </div>
  `).join('')}</div>`;
}

function renderPresetButtons() {
  els.queryPresets.innerHTML = QUERY_PRESETS.map((preset) => `
    <button class="chip action" type="button" data-preset-query="${escapeAttribute(preset.query)}" data-preset-language="${escapeAttribute(preset.language)}">
      ${escapeHtml(preset.label)}
    </button>
  `).join('');
}

function populateNodeSelects() {
  els.beforeNode.innerHTML = BEFORE_NODE_OPTIONS.map((option) => `
    <option value="${escapeAttribute(option.value)}">${escapeHtml(option.label)}</option>
  `).join('');

  els.rerunNode.innerHTML = RERUN_NODE_OPTIONS.map((option) => `
    <option value="${escapeAttribute(option.value)}">${escapeHtml(option.label)}</option>
  `).join('');
}

function syncFormControls() {
  els.taskQuery.value = state.queryText;
  els.taskLanguage.value = state.language;
  els.streamToggle.checked = state.liveEnabled;
  els.beforeNode.value = state.beforeNode;
  els.rerunNode.value = state.rerunNode;
  els.statePatch.value = state.statePatchText;
}

function syncRecoveryControls() {
  const checkpoints = safeArray(state.timeline?.checkpoints);
  const options = [
    '<option value="">选择检查点</option>',
    ...checkpoints.map((entry) => `
      <option value="${escapeAttribute(entry.checkpointId)}">
        ${escapeHtml(`${shortId(entry.checkpointId)} · ${humanizeStage(entry.nodeName)} · ${humanizeSaveMode(entry.saveMode || 'snapshot')}`)}
      </option>
    `)
  ];
  els.resumeCheckpoint.innerHTML = options.join('');

  const checkpointIds = new Set(checkpoints.map((entry) => entry.checkpointId));
  if (!state.resumeCheckpointId || !checkpointIds.has(state.resumeCheckpointId)) {
    state.resumeCheckpointId = checkpoints[0]?.checkpointId || '';
  }

  if (!state.beforeNode) {
    els.beforeNode.value = '';
  }
  els.resumeCheckpoint.value = state.resumeCheckpointId;
}

function getSelectedTaskStatus() {
  const task = getTaskSnapshot();
  return statusLabel(task?.status || 'IDLE');
}

function renderTaskList() {
  if (!state.tasks.length) {
    els.taskList.innerHTML = renderEmptyState('暂无任务', '创建一个研究任务即可启动图执行。');
    return;
  }

  els.taskList.innerHTML = state.tasks.map((task) => {
    const active = task.id === state.selectedTaskId ? 'is-active' : '';
    const tone = statusTone(task.status);
    const summary = truncate(task.query || '', 96);
    return `
      <button class="stack-item ${active}" type="button" data-task-id="${escapeAttribute(task.id)}">
        <div class="stack-item-head">
          <div>
            <div class="stack-item-title">${escapeHtml(summary || '未命名任务')}</div>
            <div class="stack-item-subtitle">${escapeHtml(`${task.language || 'zh-CN'} · ${formatDate(task.updatedAt || task.createdAt)}`)}</div>
          </div>
          ${renderPill(statusLabel(task.status), tone)}
        </div>
        <div class="stack-item-meta">
          <span>#${escapeHtml(shortId(task.id))}</span>
          <span>${escapeHtml(formatDate(task.updatedAt || task.createdAt))}</span>
        </div>
      </button>
    `;
  }).join('');
}

function renderDocumentList() {
  if (!state.documents.length) {
    els.documentList.innerHTML = renderEmptyState('暂无文档', '上传一个 UTF-8 文本文件并索引到知识库。');
    return;
  }

  els.documentList.innerHTML = state.documents.map((document) => {
    const active = document.id === state.selectedDocumentId ? 'is-active' : '';
    const tone = statusTone(document.status);
    const subtitle = document.status === 'FAILED' && document.errorMessage
      ? truncate(document.errorMessage, 84)
      : `${document.mediaType || '未知'} · ${formatDate(document.indexedAt || document.uploadedAt)}`;
    return `
      <button class="stack-item ${active}" type="button" data-document-id="${escapeAttribute(document.id)}">
        <div class="stack-item-head">
          <div>
            <div class="stack-item-title">${escapeHtml(document.originalFilename || '未命名文档')}</div>
            <div class="stack-item-subtitle">${escapeHtml(subtitle)}</div>
          </div>
          ${renderPill(statusLabel(document.status), tone)}
        </div>
        <div class="stack-item-meta">
          <span>#${escapeHtml(shortId(document.id))}</span>
          <span>${escapeHtml(formatDate(document.uploadedAt))}</span>
        </div>
      </button>
    `;
  }).join('');
}

function renderHeader() {
  const task = getTaskSnapshot();
  const title = task?.query ? truncate(task.query, 84) : '研究控制台';
  const subtitle = task
    ? `${task.language || 'zh-CN'} · ${statusLabel(task.status)} · ${formatDate(task.updatedAt || task.createdAt)}`
    : '创建任务，启动图执行，并实时查看过程。';

  els.currentTaskTitle.textContent = title;
  els.currentTaskSubtitle.textContent = subtitle;
}

function renderStatusStrip() {
  const task = getTaskSnapshot();
  const metrics = safeObject(state.timeline?.metrics);
  const evaluation = state.timeline?.latestEvaluation || null;

  els.statusValue.textContent = task ? statusLabel(task.status) : (state.loadingTask ? '加载中…' : '待机');
  els.evidenceValue.textContent = formatNumber(task?.evidenceCount ?? metrics.retrievalCount ?? 0);
  els.checkpointValue.textContent = formatNumber(metrics.checkpointCount ?? safeArray(state.timeline?.checkpoints).length ?? 0);
  els.evaluationValue.textContent = evaluation ? formatPercent(evaluation.overallScore, 0) : '--';
}

function renderCurrentChips() {
  const task = getTaskSnapshot();
  const metrics = safeObject(state.timeline?.metrics);
  if (!task) {
    els.currentChips.innerHTML = [
      renderChip('LangChain4j', 'info'),
      renderChip('LangGraph4j', 'success'),
      renderChip('SSE 实时流', 'muted'),
      renderChip('检查点', 'warning')
    ].join('');
    return;
  }

  const chips = [
    renderChip(`任务 ${shortId(task.id)}`, 'muted'),
    renderChip(`证据 ${formatNumber(task.evidenceCount ?? metrics.retrievalCount ?? 0)}`, 'info'),
    renderChip(`检查点 ${formatNumber(metrics.checkpointCount ?? safeArray(state.timeline?.checkpoints).length ?? 0)}`, 'warning'),
    renderChip(`评分 ${state.timeline?.latestEvaluation ? formatPercent(state.timeline.latestEvaluation.overallScore, 0) : '--'}`, 'success')
  ];

  if (task.plan?.needExternalSearch !== undefined) {
    chips.push(renderChip(task.plan.needExternalSearch ? '已开启外部检索' : '仅内部知识库', task.plan.needExternalSearch ? 'info' : 'muted'));
  }
  chips.push(renderChip(state.liveEnabled ? '实时流开启' : '实时流暂停', state.liveEnabled ? 'success' : 'muted'));
  chips.push(renderChip('结论级验证', 'warning'));
  els.currentChips.innerHTML = chips.join('');
}

function buildWorkflowStatus(stageKey) {
  if (!state.taskDetail && !state.timeline) {
    return { status: 'PENDING', message: '等待任务', payload: {} };
  }

  const events = safeArray(state.timeline?.progressEvents);
  const latest = [...events].reverse().find((event) => event.stage === stageKey);
  if (latest) {
    return latest;
  }

  if (stageKey === 'task') {
    const task = getTaskSnapshot();
    if (task) {
      return {
        stage: 'task',
        status: task.status || 'CREATED',
        message: task.errorMessage || '任务已就绪',
        timestamp: task.updatedAt || task.createdAt,
        payload: {}
      };
    }
  }

  return { status: 'PENDING', message: WORKFLOW_STAGES.find((item) => item.key === stageKey)?.fallback || '待处理', payload: {} };
}

function renderWorkflowRail() {
  if (!state.taskDetail && !state.loadingTask && !state.timeline) {
    els.workflowRail.innerHTML = renderEmptyState(
      '选择一个任务查看',
      '选中任务后，这里会展示规划、检索、验证、写作、审查、检查点与实时事件。',
      [
        renderChip('图编排', 'info'),
        renderChip('结论验证', 'warning'),
        renderChip('检查点恢复', 'success')
      ].join('')
    );
    return;
  }

  const task = getTaskSnapshot();
  els.workflowRail.innerHTML = WORKFLOW_STAGES.map((stage, index) => {
    const snapshot = buildWorkflowStatus(stage.key);
    const tone = statusTone(snapshot.status);
    const chips = Object.entries(safeObject(snapshot.payload))
      .slice(0, 3)
      .map(([key, value]) => renderChip(`${prettyLabel(key)}: ${formatValue(value, 36)}`, 'secondary'))
      .join('');
    return `
      <article class="rail-card is-${slugifyStatus(snapshot.status)}">
        <div class="rail-card-head">
          <div>
            <div class="rail-index">${index + 1}</div>
            <h3>${escapeHtml(stage.label)}</h3>
          </div>
          ${renderPill(statusLabel(snapshot.status), tone)}
        </div>
        <p class="rail-message">${escapeHtml(snapshot.message || stage.fallback)}</p>
        <div class="rail-foot">
          <span>${escapeHtml(formatDate(snapshot.timestamp || task?.updatedAt || task?.createdAt))}</span>
          <div class="mini-chips">${chips}</div>
        </div>
      </article>
    `;
  }).join('');
}

function renderOverviewTab() {
  if (state.loadingTask && !state.taskDetail) {
    els.tabOverview.innerHTML = renderEmptyState(
      '正在加载任务包',
      '正在获取任务详情、时间线、报告与检查点历史。'
    );
    return;
  }

  const task = getTaskSnapshot();
  if (!task) {
    els.tabOverview.innerHTML = renderEmptyState(
      '未选择任务',
      '创建一个任务，或从队列中选择一个任务查看图执行。',
      [
        renderChip('使用示例', 'info'),
        renderChip('运行当前任务', 'success'),
        renderChip('预览检查点', 'warning')
      ].join('')
    );
    return;
  }

  const detail = state.taskDetail || {};
  const plan = detail.plan || task.plan || null;
  const metrics = safeObject(state.timeline?.metrics);
  const latestEvaluation = state.timeline?.latestEvaluation || null;
  const checkpoints = safeArray(state.timeline?.checkpoints);

  const briefBody = `
    <div class="summary-meta">
      <div class="summary-line"><label>问题</label><strong>${escapeHtml(truncate(task.query || '', 220))}</strong></div>
      <div class="summary-line"><label>状态</label><strong>${escapeHtml(statusLabel(task.status))}</strong></div>
      <div class="summary-line"><label>语言</label><strong>${escapeHtml(task.language || 'zh-CN')}</strong></div>
      <div class="summary-line"><label>创建时间</label><strong>${escapeHtml(formatDate(task.createdAt || detail.createdAt))}</strong></div>
      <div class="summary-line"><label>更新时间</label><strong>${escapeHtml(formatDate(task.updatedAt || detail.updatedAt))}</strong></div>
      ${task.errorMessage ? `<div class="summary-line"><label>错误</label><strong>${escapeHtml(task.errorMessage)}</strong></div>` : ''}
    </div>
  `;

  const planBody = plan ? `
    <div class="summary-copy" style="margin:0 0 12px;font-size:13px;white-space:pre-wrap;">${escapeHtml(plan.objectiveSummary || '暂无目标摘要')}</div>
    <div class="summary-tags">${renderChips([plan.needExternalSearch ? '已启用外部检索' : '仅内部来源'], plan.needExternalSearch ? 'info' : 'muted')}</div>
    ${plan.retrievalStrategy ? `<div class="summary-copy" style="margin:12px 0 0;font-size:12px;color:var(--muted);white-space:pre-wrap;">${escapeHtml(plan.retrievalStrategy)}</div>` : ''}
    <div class="section-title" style="margin-top:14px;">研究维度</div>
    ${renderKeyValueGrid(Object.fromEntries((plan.dimensions || []).map((dimension, index) => [
      `${index + 1}. ${dimension.name || '维度'}`,
      dimension.rationale || '暂无说明'
    ])), '暂无维度')}
    <div class="section-title" style="margin-top:14px;">子查询</div>
    <div class="summary-tags">${renderChips(plan.subQueries || [], 'secondary')}</div>
    <div class="section-title" style="margin-top:14px;">事实结构</div>
    ${renderKeyValueGrid(Object.fromEntries((plan.factSchema || []).map((field, index) => [
      `${index + 1}. ${field.name || '字段'}`,
      field.description || '暂无说明'
    ])), '暂无事实结构')}
  ` : renderEmptyState('暂无规划输出', '规划器尚未生成结构化计划。');

  const evidenceBody = `
    <div class="metric-grid">
      ${renderMetricCard({ label: '证据', value: detail.evidenceCount ?? metrics.retrievalCount ?? 0, subtitle: '合并后的证据数量' })}
      ${renderMetricCard({ label: '检查点', value: metrics.checkpointCount ?? checkpoints.length ?? 0, subtitle: '持久化图快照' })}
      ${renderMetricCard({ label: 'Token 消耗', value: metrics.tokenUsage ?? 0, subtitle: 'Agent 与工具调用消耗' })}
      ${renderMetricCard({ label: '延迟', value: formatDuration(metrics.latencyMs ?? 0), subtitle: 'Agent 执行耗时总和' })}
    </div>
    <div class="summary-copy" style="margin:14px 0 0;font-size:12px;color:var(--muted);white-space:pre-wrap;">引用覆盖率 ${formatPercent(metrics.citationCoverage ?? 0, 0)} · 报告 ${detail.reportAvailable ? '已就绪' : '未就绪'} · 检查点 ${checkpoints.length}</div>
  `;

  const evaluationBody = latestEvaluation ? `
    <div class="evaluation-hero">
      <div class="score-ring" style="--score:${Math.max(0, Math.min(100, normalizePercent(latestEvaluation.overallScore) || 0))}">
        <div>
          <strong>${formatPercent(latestEvaluation.overallScore, 0)}</strong>
          <span>总分</span>
        </div>
      </div>
      <div class="evaluation-copy">
        <h3>${latestEvaluation.reviewApproved ? '审查通过' : '等待审查'}</h3>
        <p>检索 ${formatPercent(latestEvaluation.retrievalHitRate, 0)} · 引用 ${formatPercent(latestEvaluation.citationCoverage, 0)} · 结论 ${formatPercent(latestEvaluation.claimSupportRate, 0)}</p>
        <div class="report-chip-row" style="margin-top:10px;">
          ${renderChip(`低置信度章节 ${latestEvaluation.lowConfidenceSectionCount}`, latestEvaluation.lowConfidenceSectionCount > 0 ? 'warning' : 'success')}
          ${renderChip(`报告 ${formatPercent(latestEvaluation.reportCompleteness, 0)}`, 'info')}
          ${renderChip(`检索 ${formatPercent(latestEvaluation.retrievalHitRate, 0)}`, 'secondary')}
        </div>
      </div>
    </div>
    <div class="kv-grid" style="margin-top:14px;">
      ${Object.entries(safeObject(latestEvaluation.details)).map(([key, value]) => `
        <div class="kv-card">
          <span>${escapeHtml(prettyLabel(key))}</span>
          <strong>${escapeHtml(formatValue(value))}</strong>
        </div>
      `).join('')}
    </div>
  ` : renderEmptyState('暂无评测', '等图执行生成报告与结论集后再进行评测。');

  els.tabOverview.innerHTML = `
    <div class="overview-grid">
      ${renderSectionCard({
        title: '研究简报',
        subtitle: '任务身份、生命周期与用户问题',
        badge: renderPill(statusLabel(task.status), statusTone(task.status)),
        body: briefBody
      })}
      ${renderSectionCard({
        title: '规划器输出',
        subtitle: '维度、子查询与检索策略',
        badge: plan ? renderPill(plan.needExternalSearch ? '外部检索' : '仅内部', plan.needExternalSearch ? 'info' : 'muted') : '',
        body: planBody
      })}
      ${renderSectionCard({
        title: '执行与证据',
        subtitle: '图指标与检查点历史',
        badge: renderPill(detail.reportAvailable ? '报告已就绪' : '报告待生成', detail.reportAvailable ? 'success' : 'warning'),
        body: evidenceBody
      })}
      ${renderSectionCard({
        title: '评测快照',
        subtitle: '最新质量信号',
        badge: latestEvaluation ? renderPill(formatPercent(latestEvaluation.overallScore, 0), statusTone(latestEvaluation.overallScore >= 0.75 ? 'COMPLETED' : 'RUNNING')) : renderPill('未运行', 'muted'),
        body: evaluationBody
      })}
    </div>
  `;
}

function renderProgressEvent(event) {
  const payloadChips = Object.entries(safeObject(event.payload))
    .slice(0, 3)
    .map(([key, value]) => renderChip(`${prettyLabel(key)}: ${formatValue(value, 48)}`, 'secondary'))
    .join('');
  return `
    <article class="timeline-item">
      <div class="timeline-item-head">
        <div>
          <div class="stack-item-title">${escapeHtml(humanizeStage(event.stage || 'event'))}</div>
          <div class="stack-item-subtitle">${escapeHtml(formatDate(event.timestamp))}</div>
        </div>
        ${renderPill(statusLabel(event.status), statusTone(event.status))}
      </div>
        <p class="timeline-item-message">${escapeHtml(event.message || '无消息')}</p>
      <div class="timeline-item-meta">${payloadChips}</div>
    </article>
  `;
}

function renderAgentRun(run) {
  const metrics = Object.entries(safeObject(run.metrics))
    .slice(0, 4)
    .map(([key, value]) => renderChip(`${prettyLabel(key)}: ${formatValue(value, 36)}`, 'secondary'))
    .join('');
  return `
    <article class="timeline-item">
      <div class="timeline-item-head">
        <div>
          <div class="stack-item-title">${escapeHtml(humanizeStage(run.nodeName || '执行节点'))}</div>
          <div class="stack-item-subtitle">${escapeHtml(`${formatDate(run.startedAt)} → ${formatDate(run.endedAt)}`)}</div>
        </div>
        ${renderPill(`${statusLabel(run.status)} · ${formatDuration(run.latencyMs)}`, statusTone(run.status))}
      </div>
      <p class="timeline-item-message">${escapeHtml(run.message || '无消息')}</p>
      <div class="timeline-item-meta">${metrics}</div>
    </article>
  `;
}

function renderToolCall(call) {
  const metrics = Object.entries(safeObject(call.metrics))
    .slice(0, 4)
    .map(([key, value]) => renderChip(`${prettyLabel(key)}: ${formatValue(value, 36)}`, 'secondary'))
    .join('');
  return `
    <article class="timeline-item">
      <div class="timeline-item-head">
        <div>
          <div class="stack-item-title">${escapeHtml(call.toolName || '工具调用')}</div>
          <div class="stack-item-subtitle">${escapeHtml(humanizeStage(call.nodeName || '节点'))}</div>
        </div>
        ${renderPill(`${statusLabel(call.status)} · ${formatDuration(call.latencyMs)}`, statusTone(call.status))}
      </div>
      <div class="timeline-item-meta">${metrics}</div>
    </article>
  `;
}

function renderCheckpointItem(checkpoint) {
  const summary = safeObject(checkpoint.stateSummary);
  const summaryChips = Object.entries(summary)
    .slice(0, 3)
    .map(([key, value]) => renderChip(`${prettyLabel(key)}: ${formatValue(value, 32)}`, 'secondary'))
    .join('');
  const active = checkpoint.checkpointId === state.resumeCheckpointId || checkpoint.checkpointId === state.recoveryPreview?.checkpointId ? 'is-active' : '';
  return `
    <button class="stack-item ${active}" type="button" data-checkpoint-id="${escapeAttribute(checkpoint.checkpointId)}">
      <div class="stack-item-head">
        <div>
          <div class="stack-item-title">${escapeHtml(`${humanizeStage(checkpoint.nodeName)} → ${humanizeStage(checkpoint.nextNodeName || '结束')}`)}</div>
          <div class="stack-item-subtitle">${escapeHtml(`${humanizeSaveMode(checkpoint.saveMode || 'snapshot')} · ${formatDate(checkpoint.createdAt)}`)}</div>
        </div>
        ${renderPill(shortId(checkpoint.checkpointId), 'muted')}
      </div>
      <div class="stack-item-meta">${summaryChips}</div>
    </button>
  `;
}

function renderCheckpointPreview(entry, label = '') {
  if (!entry) {
    return renderEmptyState(
      '未选择快照',
      '从列表中选一个检查点，或先选一个节点再预览。'
    );
  }

  const summaryGrid = renderKeyValueGrid(entry.stateSummary, '暂无摘要数据');
  const snapshotGrid = entry.stateSnapshot
    ? `<pre class="json-box">${escapeHtml(JSON.stringify(entry.stateSnapshot, null, 2))}</pre>`
    : `<div class="empty-state"><h3>没有快照内容</h3><p>这个检查点不包含状态快照。</p></div>`;

  return `
    <div class="summary-head">
      <div>
        <h3 style="margin:0;">${escapeHtml(label || shortId(entry.checkpointId))}</h3>
        <p>${escapeHtml(`${humanizeStage(entry.nodeName)} → ${humanizeStage(entry.nextNodeName || '结束')} · ${formatDate(entry.updatedAt || entry.createdAt)}`)}</p>
      </div>
      ${renderPill(humanizeSaveMode(entry.saveMode || 'snapshot'), statusTone(entry.saveMode))}
    </div>
    <div class="snapshot-meta">
      <div class="snapshot-kv"><label>检查点</label><strong>${escapeHtml(entry.checkpointId)}</strong></div>
      <div class="snapshot-kv"><label>节点</label><strong>${escapeHtml(entry.nodeName || '—')}</strong></div>
      <div class="snapshot-kv"><label>下一个</label><strong>${escapeHtml(entry.nextNodeName || '结束')}</strong></div>
      <div class="snapshot-kv"><label>模式</label><strong>${escapeHtml(humanizeSaveMode(entry.saveMode || 'snapshot'))}</strong></div>
    </div>
    ${summaryGrid}
    ${snapshotGrid}
  `;
}

function renderTimelineTab() {
  if (state.loadingTask && !state.timeline) {
    els.tabTimeline.innerHTML = renderEmptyState('正在加载时间线', '等待进度事件、Agent 运行与检查点历史。');
    return;
  }

  if (!state.timeline) {
    els.tabTimeline.innerHTML = renderEmptyState(
      '暂无时间线',
      '运行一次图之后，这里会出现进度事件、Agent 运行、工具调用和检查点。'
    );
    return;
  }

  const metrics = safeObject(state.timeline.metrics);
  const progressEvents = safeArray(state.timeline.progressEvents);
  const agentRuns = safeArray(state.timeline.agentRuns);
  const toolCalls = safeArray(state.timeline.toolCalls);
  const checkpoints = safeArray(state.timeline.checkpoints);
  const latestEvaluation = state.timeline.latestEvaluation;

  els.tabTimeline.innerHTML = `
    <div class="metric-grid">
      ${renderMetricCard({ label: 'Token 消耗', value: metrics.tokenUsage ?? 0, subtitle: 'Agent + 工具调用' })}
      ${renderMetricCard({ label: '延迟', value: formatDuration(metrics.latencyMs ?? 0), subtitle: '累计 Agent 耗时' })}
      ${renderMetricCard({ label: '检索次数', value: metrics.retrievalCount ?? 0, subtitle: '证据记录' })}
      ${renderMetricCard({ label: '引用覆盖率', value: metrics.citationCoverage ?? 0, subtitle: '有证据支撑的结论', ratio: true })}
      ${renderMetricCard({ label: '检查点数', value: metrics.checkpointCount ?? checkpoints.length ?? 0, subtitle: '持久化图状态' })}
    </div>

    <div class="split-grid">
      <section class="summary-card">
        <div class="summary-head">
          <div>
            <h3>进度事件</h3>
            <p>按时间排列的 SSE / 图轨迹</p>
          </div>
          ${renderPill(`${progressEvents.length} 条事件`, 'info')}
        </div>
        <div class="timeline-list">
          ${(progressEvents.length ? progressEvents : [{ stage: 'event', status: 'PENDING', message: '暂无事件', timestamp: null, payload: {} }])
            .map((event) => renderProgressEvent(event))
            .join('')}
        </div>
      </section>

      <section class="summary-card">
        <div class="summary-head">
          <div>
            <h3>Agent 运行</h3>
            <p>节点执行结果与耗时</p>
          </div>
          ${renderPill(`${agentRuns.length} 次运行`, 'muted')}
        </div>
        <div class="timeline-list">
          ${(agentRuns.length ? agentRuns : [{ nodeName: 'agent', status: 'PENDING', message: '暂无 Agent 运行', startedAt: null, endedAt: null, latencyMs: 0, metrics: {} }])
            .map((run) => renderAgentRun(run))
            .join('')}
        </div>
      </section>
    </div>

    <div class="split-grid">
      <section class="summary-card">
        <div class="summary-head">
          <div>
            <h3>工具调用</h3>
            <p>检索、抓取、重排与引用助手</p>
          </div>
          ${renderPill(`${toolCalls.length} 次调用`, 'warning')}
        </div>
        <div class="timeline-list">
          ${(toolCalls.length ? toolCalls : [{ toolName: '工具调用', nodeName: '节点', status: 'PENDING', latencyMs: 0, metrics: {} }])
            .map((call) => renderToolCall(call))
            .join('')}
        </div>
      </section>

      <section class="summary-card">
        <div class="summary-head">
          <div>
            <h3>检查点轨迹</h3>
            <p>最新快照优先</p>
          </div>
          ${renderPill(`${checkpoints.length} 个快照`, 'success')}
        </div>
        <div class="stack-list">
          ${(checkpoints.length ? checkpoints : [{ checkpointId: 'none', nodeName: 'checkpoint', nextNodeName: '', saveMode: 'snapshot', stateSummary: {}, createdAt: null }])
            .map((checkpoint) => checkpoints.length ? renderCheckpointItem(checkpoint) : renderEmptyState('暂无检查点', '图还没有持久化任何快照。'))
            .join('')}
        </div>
      </section>
    </div>

    <div class="summary-card">
      <div class="summary-head">
        <div>
          <h3>最新评测</h3>
          <p>评测服务给出的质量分</p>
        </div>
        ${latestEvaluation ? renderPill(formatPercent(latestEvaluation.overallScore, 0), statusTone(latestEvaluation.overallScore >= 0.75 ? 'COMPLETED' : 'RUNNING')) : renderPill('未运行', 'muted')}
      </div>
      ${latestEvaluation ? `
        <div class="metric-grid" style="margin-top:12px;">
          ${renderMetricCard({ label: '总分', value: latestEvaluation.overallScore, subtitle: '加权质量信号', ratio: true })}
          ${renderMetricCard({ label: '检索命中率', value: latestEvaluation.retrievalHitRate, subtitle: '按子查询统计', ratio: true })}
          ${renderMetricCard({ label: '引用覆盖率', value: latestEvaluation.citationCoverage, subtitle: '有证据支撑的结论', ratio: true })}
          ${renderMetricCard({ label: '结论支撑率', value: latestEvaluation.claimSupportRate, subtitle: '已支撑与部分支撑的结论', ratio: true })}
          ${renderMetricCard({ label: '报告完整度', value: latestEvaluation.reportCompleteness, subtitle: '章节与摘要完整性', ratio: true })}
        </div>
      ` : renderEmptyState('暂无评测', '报告就绪后点击评测即可。')}
    </div>
  `;
}

function renderReportTab() {
  if (state.loadingTask && !state.report && !state.timeline) {
    els.tabReport.innerHTML = renderEmptyState('正在加载报告', '等待报告草稿与 Markdown 正文到达。');
    return;
  }

  if (!state.report) {
    els.tabReport.innerHTML = renderEmptyState(
      '暂无报告',
      '写作与审查完成后，最终 Markdown 报告会出现在这里。'
    );
    return;
  }

  const draft = state.report.draft || {};
  const sections = safeArray(draft.sections);
  const citations = safeArray(state.report.citations);
  const citationIndex = new Map(citations.map((citation) => [String(citation.id), citation]));
  const sectionGrid = sections.length ? sections.map((section) => `
    <article class="report-section ${section.lowConfidence ? 'is-low-confidence' : ''}">
      <div class="summary-head">
        <div>
          <h4>${escapeHtml(section.heading || '章节')}</h4>
          <p>${escapeHtml(section.lowConfidence ? '低置信度' : '已支撑')}</p>
        </div>
        ${renderPill(section.evidenceIds?.length ? `${section.evidenceIds.length} 条证据` : '无证据', section.evidenceIds?.length ? 'success' : 'warning')}
      </div>
      <div style="font-size:13px;white-space:pre-wrap;">${renderMarkdown(section.content || '')}</div>
      <div class="report-chip-row">${renderChips(section.evidenceIds || [], 'secondary')}</div>
      ${renderSectionCitations(section.evidenceIds || [], citationIndex)}
    </article>
  `).join('') : renderEmptyState('暂无报告章节', '写作者还没有把报告拆成章节。');
  const citationLibrary = citations.length ? `
    <div class="summary-card">
      <div class="summary-head">
        <div>
          <h3>引用来源</h3>
          <p>报告章节关联到的证据详情</p>
        </div>
        ${renderPill(`${citations.length} 条来源`, 'info')}
      </div>
      <div class="citation-list is-library">
        ${citations.map((citation) => renderCitationItem(citation)).join('')}
      </div>
    </div>
  ` : '';
  els.tabReport.innerHTML = `
    <div class="summary-card">
      <div class="report-head">
        <div>
          <h3 class="report-title">${escapeHtml(state.report.title || draft.title || '研究报告')}</h3>
          <div class="report-meta">更新时间 ${escapeHtml(formatDate(state.report.updatedAt))}</div>
        </div>
        <div class="report-chip-row">
          ${renderPill(sections.length ? `${sections.length} 个章节` : '无章节', sections.length ? 'info' : 'muted')}
          ${renderPill(draft.confidenceNote ? '置信度说明' : '无置信度说明', draft.confidenceNote ? 'warning' : 'muted')}
        </div>
      </div>
      <p style="margin:12px 0 0;font-size:13px;white-space:pre-wrap;">${escapeHtml(draft.executiveSummary || '暂无执行摘要')}</p>
      ${draft.reviewSummary ? `<div class="summary-copy" style="margin-top:12px;font-size:12px;color:var(--muted);white-space:pre-wrap;">${escapeHtml(draft.reviewSummary)}</div>` : ''}
      ${draft.confidenceNote ? `<div class="summary-copy" style="margin-top:12px;font-size:12px;color:var(--warning);white-space:pre-wrap;">${escapeHtml(draft.confidenceNote)}</div>` : ''}
      ${draft.closingSummary ? `<div class="summary-copy" style="margin-top:12px;font-size:12px;color:var(--text);white-space:pre-wrap;">${escapeHtml(draft.closingSummary)}</div>` : ''}
    </div>

    <div class="overview-grid">
      ${sectionGrid}
    </div>

    ${citationLibrary}

    <div class="summary-card">
      <div class="summary-head">
        <div>
          <h3>Markdown 正文</h3>
          <p>由最终报告载荷渲染</p>
        </div>
        ${renderPill('Markdown', 'muted')}
      </div>
      <div class="report-markdown">${renderMarkdown(state.report.markdown || '')}</div>
    </div>
  `;
}

function renderCheckpointTab() {
  if (state.loadingTask && !state.timeline) {
    els.tabCheckpoints.innerHTML = renderEmptyState('正在加载检查点', '等待图快照同步。');
    return;
  }

  const checkpoints = safeArray(state.timeline?.checkpoints);
  if (!checkpoints.length) {
    els.tabCheckpoints.innerHTML = renderEmptyState(
      '暂无检查点',
      '图一旦持久化状态快照，就可以在这里预览、恢复或重跑。'
    );
    return;
  }

  els.tabCheckpoints.innerHTML = `
    <div class="split-grid">
      <section class="summary-card">
        <div class="summary-head">
          <div>
            <h3>检查点列表</h3>
            <p>点击任意快照即可查看状态</p>
          </div>
          ${renderPill(`${checkpoints.length} 个检查点`, 'success')}
        </div>
        <div class="stack-list">
          ${checkpoints.map((checkpoint) => renderCheckpointItem(checkpoint)).join('')}
        </div>
      </section>
      <section class="summary-card">
        <div class="summary-head">
          <div>
            <h3>快照预览</h3>
            <p>${escapeHtml(state.recoveryPreviewLabel || '已选检查点或节点前状态')}</p>
          </div>
          ${state.recoveryPreview ? renderPill(state.previewMode === 'beforeNode' ? '节点前' : '检查点', state.previewMode === 'beforeNode' ? 'info' : 'warning') : renderPill('空', 'muted')}
        </div>
        ${renderCheckpointPreview(state.recoveryPreview, state.recoveryPreviewLabel)}
      </section>
    </div>
  `;
}

function renderEvaluationTab() {
  const evaluation = state.timeline?.latestEvaluation || null;
  if (!evaluation) {
    els.tabEvaluation.innerHTML = renderEmptyState(
      '暂无评测',
      '报告就绪后，运行评测即可打分检索、引用覆盖率、结论支撑率与完整度。'
    );
    return;
  }

  const details = safeObject(evaluation.details);
  els.tabEvaluation.innerHTML = `
    <div class="summary-card">
      <div class="evaluation-hero">
        <div class="score-ring" style="--score:${Math.max(0, Math.min(100, normalizePercent(evaluation.overallScore) || 0))}">
          <div>
            <strong>${formatPercent(evaluation.overallScore, 0)}</strong>
            <span>总分</span>
          </div>
        </div>
        <div class="evaluation-copy">
          <h3>${evaluation.reviewApproved ? '审查通过' : '等待审查'}</h3>
          <p>${formatPercent(evaluation.retrievalHitRate, 0)} 检索 · ${formatPercent(evaluation.citationCoverage, 0)} 引用 · ${formatPercent(evaluation.claimSupportRate, 0)} 结论支撑</p>
          <div class="report-chip-row" style="margin-top:10px;">
            ${renderChip(`低置信度章节 ${evaluation.lowConfidenceSectionCount}`, evaluation.lowConfidenceSectionCount > 0 ? 'warning' : 'success')}
            ${renderChip(`报告 ${formatPercent(evaluation.reportCompleteness, 0)}`, 'info')}
            ${renderChip(`审查 ${evaluation.reviewApproved ? '通过' : '待处理'}`, evaluation.reviewApproved ? 'success' : 'warning')}
          </div>
        </div>
      </div>
    </div>

    <div class="metric-grid">
      ${renderMetricCard({ label: '检索命中率', value: evaluation.retrievalHitRate, subtitle: '合并证据与子查询对比', ratio: true })}
      ${renderMetricCard({ label: '引用覆盖率', value: evaluation.citationCoverage, subtitle: '有证据支撑的结论', ratio: true })}
      ${renderMetricCard({ label: '结论支撑率', value: evaluation.claimSupportRate, subtitle: '已支撑与部分支撑结论', ratio: true })}
      ${renderMetricCard({ label: '报告完整度', value: evaluation.reportCompleteness, subtitle: '章节覆盖与摘要完整性', ratio: true })}
      ${renderMetricCard({ label: '总分', value: evaluation.overallScore, subtitle: '加权分数', ratio: true })}
      ${renderMetricCard({ label: '低置信度章节', value: evaluation.lowConfidenceSectionCount, subtitle: '被审查标记的章节' })}
    </div>

    <div class="summary-card">
      <div class="summary-head">
        <div>
          <h3>评测详情</h3>
          <p>派生计数与审查备注</p>
        </div>
        ${renderPill(`评测 ${shortId(evaluation.evaluationId)}`, 'muted')}
      </div>
      ${renderKeyValueGrid(details, '暂无详情')}
    </div>
  `;
}

function renderRecoveryPreview() {
  if (!state.recoveryPreview) {
    els.recoveryPreview.innerHTML = renderEmptyState(
      '选择检查点或节点',
      '使用检查点下拉框、选择节点前快照，或直接点击检查点卡片。'
    );
    return;
  }
  els.recoveryPreview.innerHTML = renderCheckpointPreview(state.recoveryPreview, state.recoveryPreviewLabel || shortId(state.recoveryPreview.checkpointId));
}

function renderLiveFeed() {
  const events = state.liveEvents.slice(-24).reverse();
  if (!events.length) {
    els.liveFeed.innerHTML = renderEmptyState(
      state.loadingTask ? '等待事件' : '暂无实时事件',
      state.liveEnabled && state.selectedTaskId ? '图一发布进度，这里就会自动出现。' : '打开实时流或先选择一个任务。'
    );
    return;
  }

  els.liveFeed.innerHTML = events.map((event) => {
    const payloadChips = Object.entries(safeObject(event.payload))
      .slice(0, 3)
      .map(([key, value]) => renderChip(`${prettyLabel(key)}: ${formatValue(value, 36)}`, 'secondary'))
      .join('');
    return `
      <article class="feed-item">
        <div class="feed-item-head">
          <div>
            <div class="stack-item-title">${escapeHtml(humanizeStage(event.stage || 'event'))}</div>
            <div class="stack-item-subtitle">${escapeHtml(formatDate(event.timestamp))}</div>
          </div>
          ${renderPill(statusLabel(event.status), statusTone(event.status))}
        </div>
        <p class="feed-item-message">${escapeHtml(event.message || '无消息')}</p>
        <div class="feed-item-payload">${payloadChips}</div>
      </article>
    `;
  }).join('');
}

function renderTabButtons() {
  const active = state.activeTab;
  qsa('.tab-btn', els.tabbar).forEach((button) => {
    button.classList.toggle('active', button.dataset.tab === active);
  });
}

function renderTabs() {
  renderTabButtons();
  qsa('.tab-panel', document).forEach((panel) => panel.classList.remove('active'));
  const map = {
    overview: els.tabOverview,
    timeline: els.tabTimeline,
    report: els.tabReport,
    checkpoints: els.tabCheckpoints,
    evaluation: els.tabEvaluation
  };
  const activePanel = map[state.activeTab] || els.tabOverview;
  activePanel.classList.add('active');

  renderOverviewTab();
  renderTimelineTab();
  renderReportTab();
  renderCheckpointTab();
  renderEvaluationTab();
}

function renderAll() {
  syncFormControls();
  syncRecoveryControls();
  renderTaskList();
  renderDocumentList();
  renderHeader();
  renderStatusStrip();
  renderCurrentChips();
  renderWorkflowRail();
  renderTabs();
  renderRecoveryPreview();
  renderLiveFeed();
  updateStreamStatus();
  refreshIcons();
}

function refreshIcons() {
  if (window.lucide?.createIcons) {
    window.lucide.createIcons();
  }
}

function updateStreamStatus() {
  els.streamStatus.className = 'pill';
  if (!state.selectedTaskId) {
    els.streamStatus.textContent = '未连接';
    els.streamStatus.classList.add('muted');
    return;
  }
  if (!state.liveEnabled) {
    els.streamStatus.textContent = '已暂停';
    els.streamStatus.classList.add('warning');
    return;
  }
  if (state.liveSource && state.liveSource.readyState === EventSource.OPEN) {
    els.streamStatus.textContent = '已连接';
    els.streamStatus.classList.add('success');
    return;
  }
  if (state.liveSource) {
    els.streamStatus.textContent = '连接中';
    els.streamStatus.classList.add('info');
    return;
  }
  els.streamStatus.textContent = '未连接';
  els.streamStatus.classList.add('muted');
}

function toast(type, title, body = '', timeout = 3200) {
  const node = document.createElement('div');
  node.className = `toast ${type}`;
  node.innerHTML = `
    <div class="title">${escapeHtml(title)}</div>
    ${body ? `<div class="body">${escapeHtml(body)}</div>` : ''}
  `;
  els.toastHost.appendChild(node);
  window.setTimeout(() => {
    node.style.opacity = '0';
    node.style.transform = 'translateY(4px)';
    node.style.transition = 'opacity 0.18s ease, transform 0.18s ease';
    window.setTimeout(() => node.remove(), 200);
  }, timeout);
}

function parseJsonInput(value, fallback = {}) {
  const text = String(value ?? '').trim();
  if (!text) {
    return fallback;
  }
  try {
    return JSON.parse(text);
  } catch (error) {
    throw new Error(`Invalid JSON: ${error.message}`);
  }
}

function eventKey(event) {
  return [
    event.timestamp || '',
    event.stage || '',
    event.status || '',
    event.message || ''
  ].join('|');
}

function seedLiveEvents(events) {
  state.liveEvents = [];
  state.liveEventKeys = new Set();
  safeArray(events).forEach((event) => {
    const normalized = {
      timestamp: event.timestamp,
      stage: event.stage,
      status: event.status,
      message: event.message,
      payload: safeObject(event.payload)
    };
    const key = eventKey(normalized);
    if (state.liveEventKeys.has(key)) {
      return;
    }
    state.liveEventKeys.add(key);
    state.liveEvents.push(normalized);
  });
}

function appendLiveEvent(event) {
  const normalized = {
    timestamp: event.timestamp,
    stage: event.stage,
    status: event.status,
    message: event.message,
    payload: safeObject(event.payload)
  };
  const key = eventKey(normalized);
  if (state.liveEventKeys.has(key)) {
    return false;
  }
  state.liveEventKeys.add(key);
  state.liveEvents.push(normalized);
  state.liveEvents = state.liveEvents.slice(-80);
  renderLiveFeed();
  scheduleRefreshCurrentTask();
  return true;
}

function scheduleRefreshCurrentTask(delay = 900) {
  if (!state.selectedTaskId) {
    return;
  }
  if (state.refreshTimer) {
    window.clearTimeout(state.refreshTimer);
  }
  state.refreshTimer = window.setTimeout(() => {
    state.refreshTimer = null;
    refreshCurrentTask({ preservePreview: true }).catch((error) => {
      console.error(error);
    });
  }, delay);
}

function disconnectLiveStream() {
  if (state.liveSource) {
    try {
      state.liveSource.close();
    } catch {
      // ignore
    }
  }
  state.liveSource = null;
  state.liveSourceTaskId = '';
  if (state.reconnectTimer) {
    window.clearTimeout(state.reconnectTimer);
    state.reconnectTimer = null;
  }
  updateStreamStatus();
}

function connectLiveStream(taskId) {
  disconnectLiveStream();
  if (!state.liveEnabled || !taskId) {
    updateStreamStatus();
    return;
  }

  const source = new EventSource(`/api/tasks/${encodeURIComponent(taskId)}/stream`);
  state.liveSource = source;
  state.liveSourceTaskId = taskId;
  updateStreamStatus();

  STREAM_STAGES.forEach((stage) => {
    source.addEventListener(stage, (event) => {
      try {
        const data = JSON.parse(event.data);
        if (appendLiveEvent(data)) {
          updateStreamStatus();
        }
      } catch (error) {
        console.error('Failed to parse SSE event', error);
      }
    });
  });

  source.onopen = () => {
    updateStreamStatus();
  };

  source.onerror = () => {
    updateStreamStatus();
    if (!state.liveEnabled || state.selectedTaskId !== taskId) {
      return;
    }
    if (state.reconnectTimer) {
      return;
    }
    state.reconnectTimer = window.setTimeout(() => {
      state.reconnectTimer = null;
      if (state.liveEnabled && state.selectedTaskId === taskId) {
        connectLiveStream(taskId);
      }
    }, 2500);
  };
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  const hasBody = options.body !== undefined && options.body !== null;
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;

  if (hasBody && !isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }

  const response = await fetch(path, {
    ...options,
    cache: 'no-store',
    headers
  });

  const contentType = response.headers.get('content-type') || '';
  let payload = null;
  if (contentType.includes('application/json')) {
    payload = await response.json();
  } else {
    const text = await response.text();
    payload = text ? text : null;
  }

  if (!response.ok) {
    const message = typeof payload === 'string'
      ? payload
      : payload?.message || payload?.detail || payload?.error || payload?.title || `Request failed (${response.status})`;
    throw new Error(message);
  }

  return payload;
}

async function refreshTasksList() {
  const seq = ++taskListLoadSeq;
  const tasks = await request('/api/tasks');
  if (seq !== taskListLoadSeq) {
    return { changedSelection: false, selectedId: state.selectedTaskId };
  }

  state.tasks = safeArray(tasks);
  const hasSelection = state.selectedTaskId && state.tasks.some((task) => task.id === state.selectedTaskId);
  let changedSelection = false;

  if (!hasSelection) {
    const firstTask = state.tasks[0]?.id || '';
    if (firstTask && firstTask !== state.selectedTaskId) {
      state.selectedTaskId = firstTask;
      changedSelection = true;
      writeStorage(STORAGE_KEYS.selectedTaskId, firstTask);
    } else if (!firstTask) {
      state.selectedTaskId = '';
      changedSelection = true;
      writeStorage(STORAGE_KEYS.selectedTaskId, '');
    }
  }

  renderTaskList();
  return { changedSelection, selectedId: state.selectedTaskId };
}

async function refreshDocumentsList() {
  const seq = ++documentListLoadSeq;
  const documents = await request('/api/documents');
  if (seq !== documentListLoadSeq) {
    return { changedSelection: false, selectedId: state.selectedDocumentId };
  }

  state.documents = safeArray(documents);
  const hasSelection = state.selectedDocumentId && state.documents.some((document) => document.id === state.selectedDocumentId);
  let changedSelection = false;

  if (!hasSelection) {
    const firstDocument = state.documents[0]?.id || '';
    if (firstDocument && firstDocument !== state.selectedDocumentId) {
      state.selectedDocumentId = firstDocument;
      changedSelection = true;
      writeStorage(STORAGE_KEYS.selectedDocumentId, firstDocument);
    } else if (!firstDocument) {
      state.selectedDocumentId = '';
      changedSelection = true;
      writeStorage(STORAGE_KEYS.selectedDocumentId, '');
    }
  }

  renderDocumentList();
  return { changedSelection, selectedId: state.selectedDocumentId };
}

async function loadTaskBundle(taskId, { beforeNode = state.beforeNode, replaceData = false, preservePreview = false } = {}) {
  if (!taskId) {
    return;
  }

  const seq = ++taskLoadSeq;
  state.loadingTask = true;
  if (replaceData) {
    state.taskDetail = null;
    state.timeline = null;
    state.report = null;
    state.recoveryPreview = null;
    state.recoveryPreviewLabel = '';
  }
  renderAll();

  try {
    const querySuffix = beforeNode ? `?beforeNode=${encodeURIComponent(beforeNode)}` : '';
    const [detail, timeline, report] = await Promise.all([
      request(`/api/tasks/${encodeURIComponent(taskId)}`),
      request(`/api/tasks/${encodeURIComponent(taskId)}/timeline${querySuffix}`),
      request(`/api/tasks/${encodeURIComponent(taskId)}/report`).catch(() => null)
    ]);

    if (seq !== taskLoadSeq) {
      return;
    }

    state.taskDetail = detail;
    state.timeline = timeline;
    state.report = report;

    seedLiveEvents(timeline?.progressEvents || []);
    syncRecoveryControls();

    const checkpoints = safeArray(timeline?.checkpoints);
    const firstCheckpoint = checkpoints[0]?.checkpointId || '';
    const resumeExists = state.resumeCheckpointId && checkpoints.some((checkpoint) => checkpoint.checkpointId === state.resumeCheckpointId);

    if (!resumeExists && firstCheckpoint) {
      state.resumeCheckpointId = firstCheckpoint;
    }

    if (!state.previewMode) {
      if (beforeNode && timeline?.selectedCheckpoint) {
        state.previewMode = 'beforeNode';
        state.beforeNode = beforeNode;
        state.recoveryPreview = timeline.selectedCheckpoint;
        state.recoveryPreviewLabel = `节点前快照：${humanizeStage(beforeNode)}`;
      } else if (beforeNode) {
        state.previewMode = 'beforeNode';
        state.beforeNode = beforeNode;
        state.recoveryPreview = null;
        state.recoveryPreviewLabel = `节点前快照：${humanizeStage(beforeNode)}`;
      } else if (state.resumeCheckpointId) {
        await loadCheckpointPreview(state.resumeCheckpointId, { keepMode: true, silent: true });
      } else if (firstCheckpoint) {
        state.previewMode = 'checkpoint';
        await loadCheckpointPreview(firstCheckpoint, { keepMode: true, silent: true });
      } else {
        state.recoveryPreview = null;
        state.recoveryPreviewLabel = '';
      }
    } else if (state.previewMode === 'beforeNode' && beforeNode && timeline?.selectedCheckpoint) {
      state.recoveryPreview = timeline.selectedCheckpoint;
      state.recoveryPreviewLabel = `节点前快照：${humanizeStage(beforeNode)}`;
    } else if (state.previewMode === 'beforeNode' && beforeNode) {
      state.recoveryPreview = null;
      state.recoveryPreviewLabel = `节点前快照：${humanizeStage(beforeNode)}`;
    } else if (state.previewMode === 'checkpoint' && state.resumeCheckpointId) {
      await loadCheckpointPreview(state.resumeCheckpointId, { keepMode: true, silent: true });
    }

    if (!preservePreview && !state.previewMode && state.resumeCheckpointId) {
      await loadCheckpointPreview(state.resumeCheckpointId, { keepMode: true, silent: true });
    }

    state.loadingTask = false;
    renderAll();
    connectLiveStream(taskId);
  } catch (error) {
    if (seq !== taskLoadSeq) {
      return;
    }
    if (String(error?.message || '').includes('Task not found')) {
      state.selectedTaskId = '';
      state.taskDetail = null;
      state.timeline = null;
      state.report = null;
      state.loadingTask = false;
      state.recoveryPreview = null;
      state.recoveryPreviewLabel = '';
      state.previewMode = '';
      state.resumeCheckpointId = '';
      state.beforeNode = '';
      state.liveEvents = [];
      state.liveEventKeys = new Set();
      disconnectLiveStream();
      writeStorage(STORAGE_KEYS.selectedTaskId, '');
      writeStorage(STORAGE_KEYS.beforeNode, '');
      writeStorage(STORAGE_KEYS.resumeCheckpointId, '');
      await refreshTasksList();
      renderAll();
      toast('info', '已清理失效任务', '之前缓存的任务不存在，已切回当前任务列表。');
      return;
    }
    state.loadingTask = false;
    renderAll();
    toast('error', '加载任务失败', error.message);
    throw error;
  }
}

async function loadCheckpointPreview(checkpointId, { keepMode = false, silent = false } = {}) {
  if (!checkpointId || !state.selectedTaskId) {
    return null;
  }

  const seq = ++previewLoadSeq;
  try {
    const preview = await request(`/api/tasks/${encodeURIComponent(state.selectedTaskId)}/checkpoints/${encodeURIComponent(checkpointId)}`);
    if (seq !== previewLoadSeq) {
      return null;
    }

    state.recoveryPreview = preview;
    state.recoveryPreviewLabel = `检查点 ${shortId(preview.checkpointId)} · ${humanizeStage(preview.nodeName)}`;
    state.resumeCheckpointId = checkpointId;
    state.previewMode = keepMode ? (state.previewMode || 'checkpoint') : 'checkpoint';
    writeStorage(STORAGE_KEYS.resumeCheckpointId, checkpointId);
    syncRecoveryControls();
    renderRecoveryPreview();
    renderCheckpointTab();
    renderCurrentChips();
    renderStatusStrip();
    if (!silent) {
      toast('info', '检查点已加载', shortId(checkpointId));
    }
    return preview;
  } catch (error) {
    if (!silent) {
      toast('error', '加载 checkpoint 失败', error.message);
    }
    return null;
  }
}

function clearTaskView() {
  state.taskDetail = null;
  state.timeline = null;
  state.report = null;
  state.loadingTask = false;
  state.recoveryPreview = null;
  state.recoveryPreviewLabel = '';
  state.previewMode = '';
  state.resumeCheckpointId = '';
  state.beforeNode = '';
  state.liveEvents = [];
  state.liveEventKeys = new Set();
  disconnectLiveStream();
  syncFormControls();
  renderAll();
}

async function selectTask(taskId, { replaceData = true } = {}) {
  if (!taskId) {
    clearTaskView();
    return;
  }

  state.selectedTaskId = taskId;
  state.activeTab = 'overview';
  state.previewMode = '';
  state.beforeNode = '';
  state.resumeCheckpointId = '';
  state.recoveryPreview = null;
  state.recoveryPreviewLabel = '';
  state.loadingTask = true;
  state.taskDetail = null;
  state.timeline = null;
  state.report = null;
  state.liveEvents = [];
  state.liveEventKeys = new Set();
  disconnectLiveStream();
  writeStorage(STORAGE_KEYS.selectedTaskId, taskId);
  writeStorage(STORAGE_KEYS.activeTab, state.activeTab);
  writeStorage(STORAGE_KEYS.beforeNode, '');
  writeStorage(STORAGE_KEYS.resumeCheckpointId, '');
  renderAll();
  await loadTaskBundle(taskId, { replaceData });
}

async function refreshCurrentTask({ preservePreview = false } = {}) {
  const taskId = getSelectedTaskId();
  if (!taskId) {
    toast('info', '没有选中任务', '先从左侧创建或选择一个研究任务。');
    return;
  }
  await loadTaskBundle(taskId, {
    beforeNode: state.beforeNode,
    replaceData: false,
    preservePreview
  });
}

async function createTaskFromForm({ runAfterCreate = false } = {}) {
  const query = els.taskQuery.value.trim();
  if (!query) {
    toast('warning', '请输入研究问题', '任务问题不能为空。');
    els.taskQuery.focus();
    return null;
  }

  const language = els.taskLanguage.value || 'zh-CN';
  const task = await request('/api/tasks', {
    method: 'POST',
    body: JSON.stringify({ query, language })
  });

  state.tasks = [task, ...state.tasks.filter((item) => item.id !== task.id)];
  state.queryText = query;
  state.language = language;
  writeStorage(STORAGE_KEYS.queryText, query);
  writeStorage(STORAGE_KEYS.language, language);
  renderTaskList();
  toast('success', '任务已创建', shortId(task.id));
  await selectTask(task.id, { replaceData: true });

  if (runAfterCreate) {
    await runTask(task.id);
  }
  return task;
}

async function runTask(taskId = getSelectedTaskId()) {
  if (!taskId) {
    toast('warning', '没有可运行的任务', '先创建一个任务，或者从任务列表里选择一个。');
    return;
  }
  await request(`/api/tasks/${encodeURIComponent(taskId)}/run`, { method: 'POST' });
  toast('info', '任务已启动', `图执行已启动：${shortId(taskId)}。`);
  await refreshCurrentTask({ preservePreview: true });
}

async function uploadSelectedDocument() {
  const file = state.pendingFile || els.documentFile.files?.[0];
  if (!file) {
    toast('warning', '请选择文件', '先从本地选择一个 UTF-8 文本文件。');
    return;
  }
  const formData = new FormData();
  formData.append('file', file);
  const document = await request('/api/documents/upload', {
    method: 'POST',
    body: formData
  });
  state.documents = [document, ...state.documents.filter((item) => item.id !== document.id)];
  state.selectedDocumentId = document.id;
  state.pendingFile = null;
  els.documentFile.value = '';
  persistDocumentSelection();
  updateDropzoneLabel(null);
  renderDocumentList();
  toast('success', '文档已上传', document.originalFilename || shortId(document.id));
  await refreshDocumentsList();
}

async function indexSelectedDocument() {
  const document = getSelectedDocument();
  if (!document) {
    toast('warning', '没有选中文档', '先在文档列表里选一个文件。');
    return;
  }
  const indexed = await request(`/api/documents/${encodeURIComponent(document.id)}/index`, {
    method: 'POST'
  });
  state.documents = [indexed, ...state.documents.filter((item) => item.id !== indexed.id)];
  state.selectedDocumentId = indexed.id;
  persistDocumentSelection();
  renderDocumentList();
  toast('success', '文档已索引', indexed.originalFilename || shortId(indexed.id));
  await refreshDocumentsList();
}

async function resumeTask() {
  const taskId = getSelectedTaskId();
  if (!taskId) {
    toast('warning', '没有选中任务', '先选择一个任务再恢复。');
    return;
  }
  const checkpointId = els.resumeCheckpoint.value || state.resumeCheckpointId;
  if (!checkpointId) {
    toast('warning', '请选择检查点', '恢复需要一个可用的检查点。');
    return;
  }
  const statePatch = parseJsonInput(els.statePatch.value, {});
  await request(`/api/tasks/${encodeURIComponent(taskId)}/resume`, {
    method: 'POST',
    body: JSON.stringify({ checkpointId, statePatch })
  });
  toast('info', '恢复已提交', `已从 ${shortId(checkpointId)} 发起恢复。`);
  await refreshCurrentTask({ preservePreview: true });
}

async function rerunTask() {
  const taskId = getSelectedTaskId();
  if (!taskId) {
    toast('warning', '没有选中任务', '先选择一个任务再重跑。');
    return;
  }
  const node = els.rerunNode.value || state.rerunNode;
  if (!node) {
    toast('warning', '请选择重跑节点', '至少选择一个可重跑的节点。');
    return;
  }
  const statePatch = parseJsonInput(els.statePatch.value, {});
  await request(`/api/tasks/${encodeURIComponent(taskId)}/rerun/${encodeURIComponent(node)}`, {
    method: 'POST',
    body: JSON.stringify({ statePatch })
  });
  toast('info', '重跑已提交', `已请求从 ${humanizeStage(node)} 开始重跑。`);
  await refreshCurrentTask({ preservePreview: true });
}

async function evaluateTask() {
  const taskId = getSelectedTaskId();
  if (!taskId) {
    toast('warning', '没有选中任务', '先选择一个任务再评测。');
    return;
  }
  const evaluation = await request(`/api/tasks/${encodeURIComponent(taskId)}/evaluate`, {
    method: 'POST'
  });
  if (state.timeline) {
    state.timeline.latestEvaluation = evaluation;
  }
  toast('success', '评测已完成', `总分 ${formatPercent(evaluation.overallScore, 0)}。`);
  renderAll();
}

async function previewSnapshot() {
  const taskId = getSelectedTaskId();
  if (!taskId) {
    toast('warning', '没有选中任务', '先选择一个任务再预览。');
    return;
  }
  const beforeNode = els.beforeNode.value.trim();
  const checkpointId = els.resumeCheckpoint.value || state.resumeCheckpointId;
  if (beforeNode) {
    state.beforeNode = beforeNode;
    state.previewMode = 'beforeNode';
    writeStorage(STORAGE_KEYS.beforeNode, beforeNode);
    await loadTaskBundle(taskId, { beforeNode, replaceData: false, preservePreview: true });
    if (!state.recoveryPreview) {
      toast('warning', '没有找到快照', `${humanizeStage(beforeNode)} 之前没有可预览的状态。`);
    } else {
      toast('info', '已预览快照', `节点前：${humanizeStage(beforeNode)}`);
    }
    return;
  }
  if (checkpointId) {
    state.previewMode = 'checkpoint';
    writeStorage(STORAGE_KEYS.resumeCheckpointId, checkpointId);
    await loadCheckpointPreview(checkpointId, { keepMode: true });
    return;
  }
  toast('warning', '请选择预览目标', '先选检查点，或者在“节点前”里选一个节点。');
}

function updateDropzoneLabel(file) {
  const strong = els.documentDropzone.querySelector('.dropzone-copy strong');
  const span = els.documentDropzone.querySelector('.dropzone-copy span');
  if (!strong || !span) {
    return;
  }
  if (file) {
    strong.textContent = file.name;
    span.textContent = `${file.type || '未知类型'} · ${formatNumber(file.size / 1024, 0)} KB`;
    return;
  }
  strong.textContent = 'Drop a file here';
  span.textContent = 'or choose a UTF-8 text file to index';
}

function persistTaskSelection() {
  writeStorage(STORAGE_KEYS.selectedTaskId, state.selectedTaskId);
}

function persistDocumentSelection() {
  writeStorage(STORAGE_KEYS.selectedDocumentId, state.selectedDocumentId);
}

function bindEvents() {
  els.queryPresets.addEventListener('click', (event) => {
    const button = event.target.closest('[data-preset-query]');
    if (!button) {
      return;
    }
    const query = button.dataset.presetQuery || DEFAULT_QUERY;
    const language = button.dataset.presetLanguage || 'zh-CN';
    state.queryText = query;
    state.language = language;
    els.taskQuery.value = query;
    els.taskLanguage.value = language;
    writeStorage(STORAGE_KEYS.queryText, query);
    writeStorage(STORAGE_KEYS.language, language);
    toast('info', '已应用示例', prettyLabel(button.textContent || 'Preset'));
  });

  els.taskQuery.addEventListener('input', () => {
    state.queryText = els.taskQuery.value;
    writeStorage(STORAGE_KEYS.queryText, state.queryText);
  });

  els.taskLanguage.addEventListener('change', () => {
    state.language = els.taskLanguage.value;
    writeStorage(STORAGE_KEYS.language, state.language);
  });

  els.createTaskBtn.addEventListener('click', async () => {
    try {
      await createTaskFromForm({ runAfterCreate: false });
    } catch (error) {
      toast('error', '创建任务失败', error.message);
    }
  });

  els.runCurrentBtn.addEventListener('click', async () => {
    try {
      if (getSelectedTaskId()) {
        await runTask();
        return;
      }
      const query = els.taskQuery.value.trim();
      if (!query) {
        toast('warning', '请输入研究问题', '先创建一个任务，或先选中一个任务。');
        return;
      }
      const task = await createTaskFromForm({ runAfterCreate: false });
      if (task) {
        await runTask(task.id);
      }
    } catch (error) {
      toast('error', '运行任务失败', error.message);
    }
  });

  els.runSelectedBtn.addEventListener('click', async () => {
    try {
      await runTask();
    } catch (error) {
      toast('error', '运行任务失败', error.message);
    }
  });

  els.refreshCurrentBtn.addEventListener('click', async () => {
    try {
      await refreshCurrentTask({ preservePreview: true });
    } catch (error) {
      toast('error', '刷新失败', error.message);
    }
  });

  els.refreshTasksBtn.addEventListener('click', async () => {
    try {
      const result = await refreshTasksList();
      if (result.changedSelection && result.selectedId) {
        await selectTask(result.selectedId, { replaceData: true });
      } else {
        renderAll();
      }
      toast('success', '任务列表已刷新', `${state.tasks.length} 条任务`);
    } catch (error) {
      toast('error', '刷新任务失败', error.message);
    }
  });

  els.refreshDocumentsBtn.addEventListener('click', async () => {
    try {
      await refreshDocumentsList();
      renderAll();
      toast('success', '文档列表已刷新', `${state.documents.length} 份文档`);
    } catch (error) {
      toast('error', '刷新文档失败', error.message);
    }
  });

  els.chooseFileBtn.addEventListener('click', () => els.documentFile.click());
  els.documentDropzone.addEventListener('click', (event) => {
    if (event.target.closest('button')) {
      return;
    }
    els.documentFile.click();
  });

  els.documentFile.addEventListener('change', () => {
    const file = els.documentFile.files?.[0] || null;
    state.pendingFile = file;
    updateDropzoneLabel(file);
    if (file) {
      toast('info', '文件已选择', file.name);
    }
  });

  els.documentDropzone.addEventListener('dragenter', (event) => {
    event.preventDefault();
    els.documentDropzone.classList.add('is-dragover');
  });
  els.documentDropzone.addEventListener('dragover', (event) => {
    event.preventDefault();
    els.documentDropzone.classList.add('is-dragover');
  });
  els.documentDropzone.addEventListener('dragleave', () => {
    els.documentDropzone.classList.remove('is-dragover');
  });
  els.documentDropzone.addEventListener('drop', (event) => {
    event.preventDefault();
    els.documentDropzone.classList.remove('is-dragover');
    const file = event.dataTransfer?.files?.[0] || null;
    if (!file) {
      return;
    }
    state.pendingFile = file;
    updateDropzoneLabel(file);
    toast('info', '文件已拖入', file.name);
  });

  els.uploadDocumentBtn.addEventListener('click', async () => {
    try {
      await uploadSelectedDocument();
    } catch (error) {
      toast('error', '上传失败', error.message);
    }
  });

  els.indexDocumentBtn.addEventListener('click', async () => {
    try {
      await indexSelectedDocument();
    } catch (error) {
      toast('error', '索引失败', error.message);
    }
  });

  els.taskList.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-task-id]');
    if (!button) {
      return;
    }
    const taskId = button.dataset.taskId;
    if (taskId === state.selectedTaskId && state.taskDetail && !state.loadingTask) {
      await refreshCurrentTask({ preservePreview: true });
      return;
    }
    try {
      await selectTask(taskId, { replaceData: true });
    } catch (error) {
      toast('error', '切换任务失败', error.message);
    }
  });

  els.documentList.addEventListener('click', (event) => {
    const button = event.target.closest('[data-document-id]');
    if (!button) {
      return;
    }
    state.selectedDocumentId = button.dataset.documentId;
    persistDocumentSelection();
    renderDocumentList();
  });

  els.tabbar.addEventListener('click', (event) => {
    const button = event.target.closest('.tab-btn');
    if (!button) {
      return;
    }
    state.activeTab = button.dataset.tab || 'overview';
    writeStorage(STORAGE_KEYS.activeTab, state.activeTab);
    renderAll();
  });

  els.resumeCheckpoint.addEventListener('change', () => {
    state.resumeCheckpointId = els.resumeCheckpoint.value;
    writeStorage(STORAGE_KEYS.resumeCheckpointId, state.resumeCheckpointId);
    if (state.resumeCheckpointId) {
      loadCheckpointPreview(state.resumeCheckpointId, { keepMode: true }).catch(() => {});
    }
    renderAll();
  });

  els.beforeNode.addEventListener('change', () => {
    state.beforeNode = els.beforeNode.value;
    writeStorage(STORAGE_KEYS.beforeNode, state.beforeNode);
    renderAll();
  });

  els.rerunNode.addEventListener('change', () => {
    state.rerunNode = els.rerunNode.value;
    writeStorage(STORAGE_KEYS.rerunNode, state.rerunNode);
  });

  els.statePatch.addEventListener('input', () => {
    state.statePatchText = els.statePatch.value;
    writeStorage(STORAGE_KEYS.statePatch, state.statePatchText);
  });

  els.streamToggle.addEventListener('change', () => {
    state.liveEnabled = els.streamToggle.checked;
    writeStorage(STORAGE_KEYS.liveEnabled, state.liveEnabled);
    if (state.liveEnabled && state.selectedTaskId) {
      connectLiveStream(state.selectedTaskId);
    } else {
      disconnectLiveStream();
      updateStreamStatus();
    }
    renderCurrentChips();
  });

  els.resumeBtn.addEventListener('click', async () => {
    try {
      await resumeTask();
    } catch (error) {
      toast('error', '恢复失败', error.message);
    }
  });

  els.rerunBtn.addEventListener('click', async () => {
    try {
      await rerunTask();
    } catch (error) {
      toast('error', '重跑失败', error.message);
    }
  });

  els.previewBeforeBtn.addEventListener('click', async () => {
    try {
      await previewSnapshot();
    } catch (error) {
      toast('error', '预览失败', error.message);
    }
  });

  els.evaluateBtn.addEventListener('click', async () => {
    try {
      await evaluateTask();
    } catch (error) {
      toast('error', '评测失败', error.message);
    }
  });

  window.addEventListener('beforeunload', () => {
    disconnectLiveStream();
  });

  els.tabCheckpoints.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-checkpoint-id]');
    if (!button) {
      return;
    }
    try {
      state.previewMode = 'checkpoint';
      await loadCheckpointPreview(button.dataset.checkpointId, { keepMode: true });
      renderTabs();
    } catch (error) {
      toast('error', '加载 checkpoint 失败', error.message);
    }
  });
}

async function boot() {
  populateNodeSelects();
  renderPresetButtons();
  syncFormControls();
  updateDropzoneLabel(null);
  bindEvents();
  refreshIcons();

  try {
    const [tasksResult, documentsResult] = await Promise.all([
      refreshTasksList(),
      refreshDocumentsList()
    ]);

    if (state.selectedTaskId) {
      await selectTask(state.selectedTaskId, { replaceData: true });
    } else {
      renderAll();
    }

    if (tasksResult.changedSelection && tasksResult.selectedId && state.selectedTaskId !== tasksResult.selectedId) {
      await selectTask(tasksResult.selectedId, { replaceData: true });
    }

    if (documentsResult.changedSelection && documentsResult.selectedId) {
      state.selectedDocumentId = documentsResult.selectedId;
      persistDocumentSelection();
    }
    renderAll();
  } catch (error) {
    console.error(error);
    toast('error', '页面加载失败', error.message);
    renderAll();
  }
}

document.addEventListener('DOMContentLoaded', boot);
