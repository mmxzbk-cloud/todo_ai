import { useState } from 'react'
import type { FormEvent } from 'react'

import { useDeleteNewsSource, useNewsSources, useSaveNewsSource, useToggleNewsSource, useTrialNewsSource } from '../../hooks/useNews.ts'
import {
  NEWS_CATEGORIES,
  NEWS_CATEGORY_LABELS,
  NEWS_SOURCE_TYPES,
  NEWS_SOURCE_TYPE_LABELS,
} from '../../types/news.ts'
import type { NewsCategory, NewsParseRule, NewsSource, NewsSourceType } from '../../types/news.ts'

const INPUT_CLASS =
  'rounded-md border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-500'

function formatTime(value: string | null): string {
  if (value == null) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

/** 网页来源的解析规则表单值（字符串三件套必填，两个可选项留空即不启用）。 */
interface ParseRuleDraft {
  item: string
  title: string
  link: string
  published: string
  content: string
}

const EMPTY_PARSE_RULE: ParseRuleDraft = { item: '', title: '', link: '', published: '', content: '' }

interface FormDraft {
  id: number | null
  name: string
  type: NewsSourceType
  url: string
  defaultCategory: NewsCategory | ''
  enabled: boolean
  parseRule: ParseRuleDraft
}

function draftFromSource(source: NewsSource): FormDraft {
  return {
    id: source.id,
    name: source.name,
    type: source.type,
    url: source.url,
    defaultCategory: source.defaultCategory ?? '',
    enabled: source.enabled,
    parseRule: {
      item: source.parseRule?.item ?? '',
      title: source.parseRule?.title ?? '',
      link: source.parseRule?.link ?? '',
      published: source.parseRule?.published ?? '',
      content: source.parseRule?.content ?? '',
    },
  }
}

const NEW_DRAFT: FormDraft = {
  id: null,
  name: '',
  type: 'RSS',
  url: '',
  defaultCategory: '',
  enabled: true,
  parseRule: EMPTY_PARSE_RULE,
}

/**
 * 来源管理（PRD US-01）：增删改查、启停、试抓与健康状态展示。
 * robots 禁止 / 上限超限等错误由后端返回，这里统一翻译成提示文案。
 */
export function NewsSourceManager() {
  const sourcesQuery = useNewsSources()
  const saveSource = useSaveNewsSource()
  const toggleSource = useToggleNewsSource()
  const deleteSource = useDeleteNewsSource()
  const trialSource = useTrialNewsSource()

  const [draft, setDraft] = useState<FormDraft | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  function startCreate() {
    setMessage(null)
    setDraft({ ...NEW_DRAFT, parseRule: { ...EMPTY_PARSE_RULE } })
  }

  function startEdit(source: NewsSource) {
    setMessage(null)
    setDraft(draftFromSource(source))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (draft == null) return
    const parseRule: NewsParseRule | null =
      draft.type === 'WEBPAGE'
        ? {
            item: draft.parseRule.item.trim(),
            title: draft.parseRule.title.trim(),
            link: draft.parseRule.link.trim(),
            published: draft.parseRule.published.trim() || null,
            content: draft.parseRule.content.trim() || null,
          }
        : null
    if (draft.type === 'WEBPAGE' && (!parseRule?.item || !parseRule.title || !parseRule.link)) {
      setMessage('网页来源必须填写 item / title / link 三个选择器')
      return
    }
    saveSource.mutate(
      {
        id: draft.id ?? undefined,
        name: draft.name.trim(),
        type: draft.type,
        url: draft.url.trim(),
        defaultCategory: draft.defaultCategory === '' ? null : draft.defaultCategory,
        enabled: draft.enabled,
        parseRule,
      },
      {
        onSuccess: () => {
          setDraft(null)
          setMessage(null)
        },
        onError: (error) => setMessage(error.message),
      },
    )
  }

  function handleTrial() {
    if (draft == null) return
    const parseRule: NewsParseRule | null =
      draft.type === 'WEBPAGE'
        ? {
            item: draft.parseRule.item.trim(),
            title: draft.parseRule.title.trim(),
            link: draft.parseRule.link.trim(),
            published: draft.parseRule.published.trim() || null,
            content: draft.parseRule.content.trim() || null,
          }
        : null
    trialSource.mutate(
      {
        name: draft.name.trim() || '试抓',
        type: draft.type,
        url: draft.url.trim(),
        defaultCategory: draft.defaultCategory === '' ? null : draft.defaultCategory,
        parseRule,
      },
      {
        onSuccess: (result) =>
          setMessage(
            result.success
              ? `试抓成功：解析出 ${result.itemCount} 条${result.titles.length > 0 ? '，如「' + result.titles[0] + '」' : ''}`
              : `试抓失败：${result.message ?? '未知原因'}`,
          ),
        onError: (error) => setMessage(error.message),
      },
    )
  }

  const sources = sourcesQuery.data ?? []

  return (
    <section aria-label="订阅来源管理" className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold">订阅来源（{sources.length}）</h2>
        <button
          type="button"
          onClick={startCreate}
          className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700"
        >
          新增来源
        </button>
      </div>

      {message != null && <p className="mb-3 text-sm text-slate-600">{message}</p>}

      {draft != null && (
        <form onSubmit={handleSubmit} className="mb-4 space-y-3 rounded-md border border-slate-200 p-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="text-sm">
              名称
              <input
                value={draft.name}
                onChange={(event) => setDraft({ ...draft, name: event.target.value })}
                className={`${INPUT_CLASS} mt-1 w-full`}
                maxLength={100}
                required
              />
            </label>
            <label className="text-sm">
              类型
              <select
                value={draft.type}
                onChange={(event) => setDraft({ ...draft, type: event.target.value as NewsSourceType })}
                className={`${INPUT_CLASS} mt-1 w-full`}
              >
                {NEWS_SOURCE_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {NEWS_SOURCE_TYPE_LABELS[type]}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <label className="block text-sm">
            地址（http/https）
            <input
              value={draft.url}
              onChange={(event) => setDraft({ ...draft, url: event.target.value })}
              className={`${INPUT_CLASS} mt-1 w-full`}
              placeholder="https://…"
              maxLength={1000}
              required
            />
          </label>
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="text-sm">
              默认分类
              <select
                value={draft.defaultCategory}
                onChange={(event) =>
                  setDraft({ ...draft, defaultCategory: event.target.value as NewsCategory | '' })
                }
                className={`${INPUT_CLASS} mt-1 w-full`}
              >
                <option value="">不指定</option>
                {NEWS_CATEGORIES.map((category) => (
                  <option key={category} value={category}>
                    {NEWS_CATEGORY_LABELS[category]}
                  </option>
                ))}
              </select>
            </label>
            <label className="mt-7 flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={draft.enabled}
                onChange={(event) => setDraft({ ...draft, enabled: event.target.checked })}
              />
              启用（参与自动采集）
            </label>
          </div>

          {draft.type === 'WEBPAGE' && (
            <fieldset className="rounded-md border border-slate-200 p-3 text-sm">
              <legend className="px-1 text-slate-600">解析规则（CSS 选择器）</legend>
              <div className="grid gap-3 sm:grid-cols-3">
                {(
                  [
                    ['item', '列表项 item *'],
                    ['title', '标题 title *'],
                    ['link', '链接 link *'],
                    ['published', '发布时间 published'],
                    ['content', '正文 content'],
                  ] as const
                ).map(([field, label]) => (
                  <label key={field}>
                    {label}
                    <input
                      value={draft.parseRule[field]}
                      onChange={(event) =>
                        setDraft({ ...draft, parseRule: { ...draft.parseRule, [field]: event.target.value } })
                      }
                      className={`${INPUT_CLASS} mt-1 w-full`}
                      placeholder=".item"
                    />
                  </label>
                ))}
              </div>
            </fieldset>
          )}

          <div className="flex gap-2">
            <button
              type="submit"
              disabled={saveSource.isPending}
              className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
            >
              {draft.id == null ? '创建' : '保存'}
            </button>
            <button
              type="button"
              onClick={handleTrial}
              disabled={trialSource.isPending}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100 disabled:opacity-50"
            >
              {trialSource.isPending ? '试抓中…' : '试抓验证'}
            </button>
            <button
              type="button"
              onClick={() => setDraft(null)}
              className="rounded-md px-3 py-1.5 text-sm text-slate-500 hover:bg-slate-100"
            >
              取消
            </button>
          </div>
        </form>
      )}

      <ul className="divide-y divide-slate-100">
        {sources.map((source) => (
          <li key={source.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2 text-sm">
            <span
              aria-hidden
              className={`inline-block h-2 w-2 rounded-full ${
                source.autoDisabled ? 'bg-red-500' : source.enabled ? 'bg-emerald-500' : 'bg-slate-300'
              }`}
            />
            <span className="font-medium">{source.name}</span>
            <span className="text-slate-500">{NEWS_SOURCE_TYPE_LABELS[source.type]}</span>
            <span className="max-w-[16rem] truncate text-slate-400" title={source.url}>
              {source.url}
            </span>
            <span className={source.lastFetchOk === false ? 'text-red-600' : 'text-slate-500'}>
              {source.lastFetchAt == null
                ? '尚未采集'
                : `${formatTime(source.lastFetchAt)}：${source.lastFetchMessage ?? ''}`}
            </span>
            {source.autoDisabled && <span className="text-red-600">连续失败已停抓</span>}
            <span className="ml-auto flex gap-2">
              <button
                type="button"
                onClick={() => toggleSource.mutate(source, { onError: (error) => setMessage(error.message) })}
                className="rounded px-2 py-1 text-slate-600 hover:bg-slate-100"
              >
                {source.enabled ? '停用' : '启用'}
              </button>
              <button
                type="button"
                onClick={() => startEdit(source)}
                className="rounded px-2 py-1 text-slate-600 hover:bg-slate-100"
              >
                编辑
              </button>
              <button
                type="button"
                onClick={() => {
                  // 二次确认（AC 1.6）：历史资讯会保留，但来源配置不可恢复
                  if (window.confirm(`删除来源「${source.name}」？已抓取的资讯会保留。`)) {
                    deleteSource.mutate(source.id, { onError: (error) => setMessage(error.message) })
                  }
                }}
                className="rounded px-2 py-1 text-red-600 hover:bg-red-50"
              >
                删除
              </button>
            </span>
          </li>
        ))}
        {sources.length === 0 && (
          <li className="py-3 text-sm text-slate-500">
            还没有订阅来源。添加 RSS 或网页列表来源后，系统会每天自动采集。
          </li>
        )}
      </ul>
    </section>
  )
}
