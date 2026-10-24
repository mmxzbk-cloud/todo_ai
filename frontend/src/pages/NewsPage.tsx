import { useState } from 'react'
import type { FormEvent } from 'react'

import { NewsSourceManager } from '../components/news/NewsSourceManager.tsx'
import { NewsItemDetailModal } from '../components/news/NewsItemDetailModal.tsx'
import { useAiStatus } from '../hooks/useAi.ts'
import { describeError } from '../hooks/useTasks.ts'
import {
  useCollectNews,
  useNewsDigest,
  useNewsList,
  useNewsSources,
  useSetNewsRead,
  useSetNewsStar,
} from '../hooks/useNews.ts'
import {
  NEWS_AI_STATUS_LABELS,
  NEWS_CATEGORIES,
  NEWS_CATEGORY_LABELS,
} from '../types/news.ts'
import type { NewsCategory, NewsListParams } from '../types/news.ts'

const INPUT_CLASS =
  'rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500'

function formatDay(value: string | null): string {
  if (value == null) return '—'
  return new Date(value).toLocaleDateString('zh-CN')
}

/**
 * 资讯页（PRD US-03/05 的列表侧 + US-01/02 的入口）：筛选检索、星标已读、
 * 每日简报、立即采集与来源管理。AI 入口（简报）只在总开关打开时出现（AC 5.1）。
 */
function NewsPage() {
  const aiEnabled = useAiStatus()

  const [category, setCategory] = useState<NewsCategory | ''>('')
  const [sourceId, setSourceId] = useState<number | ''>('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [starred, setStarred] = useState(false)
  const [unread, setUnread] = useState(false)
  const [keywordInput, setKeywordInput] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)
  const [showSources, setShowSources] = useState(false)
  const [openItemId, setOpenItemId] = useState<number | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [digestReport, setDigestReport] = useState<string | null>(null)

  const params: NewsListParams = {
    category: category === '' ? undefined : category,
    sourceId: sourceId === '' ? undefined : sourceId,
    from: from === '' ? undefined : from,
    to: to === '' ? undefined : to,
    starred: starred || undefined,
    unread: unread || undefined,
    q: keyword === '' ? undefined : keyword,
    page,
    size: 20,
  }

  const listQuery = useNewsList(params)
  const sourcesQuery = useNewsSources()
  const setRead = useSetNewsRead()
  const setStar = useSetNewsStar()
  const collect = useCollectNews()
  const digest = useNewsDigest()

  const items = listQuery.data?.items ?? []
  const total = listQuery.data?.total ?? 0

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setKeyword(keywordInput.trim())
  }

  function handleCollect() {
    setMessage(null)
    collect.mutate(undefined, {
      onSuccess: (result) => setMessage(`已触发采集：${result.queued} 个来源，完成后状态见来源管理`),
      onError: (error) => setMessage(describeError(error)),
    })
  }

  function handleDigest() {
    setMessage(null)
    digest.mutate(undefined, {
      onSuccess: (result) => setDigestReport(result.report),
      onError: (error) => setMessage(describeError(error)),
    })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-semibold">资讯</h1>
        <span className="text-sm text-slate-500">政策与热点的自动采集与检索</span>
        <div className="ml-auto flex gap-2">
          <button
            type="button"
            onClick={() => setShowSources((previous) => !previous)}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100"
          >
            {showSources ? '收起来源管理' : '来源管理'}
          </button>
          <button
            type="button"
            onClick={handleCollect}
            disabled={collect.isPending}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100 disabled:opacity-50"
          >
            {collect.isPending ? '触发中…' : '立即采集'}
          </button>
          {aiEnabled.data === true && (
            <button
              type="button"
              onClick={handleDigest}
              disabled={digest.isPending}
              className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
            >
              {digest.isPending ? '简报生成中…' : '生成今日简报'}
            </button>
          )}
        </div>
      </div>

      {message != null && <p className="text-sm text-slate-600">{message}</p>}

      {digestReport != null && (
        <section aria-label="每日简报" className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="mb-2 flex items-center justify-between">
            <h2 className="text-sm font-semibold">今日简报</h2>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => {
                  void navigator.clipboard?.writeText(digestReport)
                  setMessage('简报已复制')
                }}
                className="rounded px-2 py-1 text-xs text-slate-600 hover:bg-slate-100"
              >
                复制
              </button>
              <button
                type="button"
                onClick={() => setDigestReport(null)}
                className="rounded px-2 py-1 text-xs text-slate-500 hover:bg-slate-100"
              >
                关闭
              </button>
            </div>
          </div>
          <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-700">{digestReport}</p>
        </section>
      )}

      {showSources && <NewsSourceManager />}

      <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-white p-4">
        <label className="text-sm">
          分类
          <select
            value={category}
            onChange={(event) => {
              setCategory(event.target.value as NewsCategory | '')
              setPage(0)
            }}
            className={`${INPUT_CLASS} mt-1 block`}
          >
            <option value="">全部</option>
            {NEWS_CATEGORIES.map((value) => (
              <option key={value} value={value}>
                {NEWS_CATEGORY_LABELS[value]}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          来源
          <select
            value={sourceId}
            onChange={(event) => {
              setSourceId(event.target.value === '' ? '' : Number(event.target.value))
              setPage(0)
            }}
            className={`${INPUT_CLASS} mt-1 block`}
          >
            <option value="">全部</option>
            {(sourcesQuery.data ?? []).map((source) => (
              <option key={source.id} value={source.id}>
                {source.name}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          入库从
          <input
            type="date"
            value={from}
            onChange={(event) => {
              setFrom(event.target.value)
              setPage(0)
            }}
            className={`${INPUT_CLASS} mt-1 block`}
          />
        </label>
        <label className="text-sm">
          到
          <input
            type="date"
            value={to}
            onChange={(event) => {
              setTo(event.target.value)
              setPage(0)
            }}
            className={`${INPUT_CLASS} mt-1 block`}
          />
        </label>
        <label className="flex items-center gap-1 pb-2 text-sm text-slate-600">
          <input
            type="checkbox"
            checked={starred}
            onChange={(event) => {
              setStarred(event.target.checked)
              setPage(0)
            }}
          />
          只看星标
        </label>
        <label className="flex items-center gap-1 pb-2 text-sm text-slate-600">
          <input
            type="checkbox"
            checked={unread}
            onChange={(event) => {
              setUnread(event.target.checked)
              setPage(0)
            }}
          />
          只看未读
        </label>
        <label className="min-w-48 flex-1 text-sm">
          关键词（标题与正文）
          <input
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
            className={`${INPUT_CLASS} mt-1 w-full`}
            placeholder="如：课程考核"
          />
        </label>
        <button
          type="submit"
          className="rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          搜索
        </button>
      </form>

      <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white">
        {items.map((item) => (
          <li key={item.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 px-4 py-3">
            <span
              aria-hidden
              className={`inline-block h-2 w-2 rounded-full ${item.readAt == null ? 'bg-slate-900' : 'bg-transparent'}`}
              title={item.readAt == null ? '未读' : '已读'}
            />
            <button
              type="button"
              onClick={() => setOpenItemId(item.id)}
              className={`max-w-[36rem] truncate text-left text-sm hover:underline ${
                item.readAt == null ? 'font-medium' : 'text-slate-700'
              }`}
            >
              {item.title}
            </button>
            <span className="text-xs text-slate-500">{item.sourceName}</span>
            {item.category != null && (
              <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
                {NEWS_CATEGORY_LABELS[item.category]}
              </span>
            )}
            <span className="text-xs text-slate-400">{formatDay(item.publishedAt ?? item.createdAt)}</span>
            {item.aiStatus !== 'DONE' && (
              <span className="text-xs text-slate-400">{NEWS_AI_STATUS_LABELS[item.aiStatus]}</span>
            )}
            <span className="ml-auto flex gap-1">
              <button
                type="button"
                onClick={() =>
                  setStar.mutate(
                    { id: item.id, starred: !item.starred },
                    { onError: (error) => setMessage(describeError(error)) },
                  )
                }
                className={`rounded px-2 py-1 text-sm hover:bg-slate-100 ${
                  item.starred ? 'text-amber-600' : 'text-slate-400'
                }`}
                aria-label={item.starred ? '取消星标' : '标星'}
              >
                {item.starred ? '★' : '☆'}
              </button>
              <button
                type="button"
                onClick={() =>
                  setRead.mutate(
                    { id: item.id, read: item.readAt == null },
                    { onError: (error) => setMessage(describeError(error)) },
                  )
                }
                className="rounded px-2 py-1 text-xs text-slate-500 hover:bg-slate-100"
              >
                {item.readAt == null ? '标已读' : '标未读'}
              </button>
            </span>
          </li>
        ))}
        {items.length === 0 && (
          <li className="px-4 py-6 text-center text-sm text-slate-500">
            没有符合条件的资讯。可以添加来源并点「立即采集」。
          </li>
        )}
      </ul>

      <div className="flex items-center justify-between text-sm text-slate-500">
        <span>共 {total} 条</span>
        <span className="flex gap-2">
          <button
            type="button"
            onClick={() => setPage((previous) => Math.max(previous - 1, 0))}
            disabled={page === 0}
            className="rounded-md border border-slate-300 px-3 py-1.5 hover:bg-slate-100 disabled:opacity-50"
          >
            上一页
          </button>
          <button
            type="button"
            onClick={() => setPage((previous) => previous + 1)}
            disabled={(page + 1) * 20 >= total}
            className="rounded-md border border-slate-300 px-3 py-1.5 hover:bg-slate-100 disabled:opacity-50"
          >
            下一页
          </button>
        </span>
      </div>

      {openItemId != null && (
        <NewsItemDetailModal itemId={openItemId} onClose={() => setOpenItemId(null)} />
      )}
    </div>
  )
}

export default NewsPage
