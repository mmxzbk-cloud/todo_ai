/**
 * 资讯域的查询与失效规则（与 useTasks 同一约定：失效口径收在一处）。
 * 所有写操作统一按 ['news'] 前缀失效：来源健康状态、列表计数、详情摘要互相影响，
 * 逐个枚举失效范围省下的几次请求，抵不过漏刷一次带来的「改了但没变」。
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { newsApi } from '../api/news.ts'
import { queryKeys } from '../api/queryKeys.ts'
import type {
  AdoptInput,
  ClassificationInput,
  ConvertTaskInput,
  NewsListParams,
  NewsSource,
  SourceInput,
} from '../types/news.ts'

// ---------------------------------------------------------------- 查询

export function useNewsSources() {
  return useQuery({ queryKey: queryKeys.newsSources, queryFn: newsApi.listSources })
}

export function useNewsList(params: NewsListParams) {
  return useQuery({
    queryKey: queryKeys.newsItems(params),
    queryFn: () => newsApi.listItems(params),
    placeholderData: (previous) => previous,
  })
}

export function useNewsItem(id: number | null) {
  return useQuery({
    queryKey: queryKeys.newsItem(id ?? 0),
    queryFn: () => newsApi.getItem(id as number),
    enabled: id != null,
  })
}

// ---------------------------------------------------------------- 来源变更

export function useSaveNewsSource() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (input: SourceInput & { id?: number }) => {
      const { id, ...body } = input
      return id == null ? newsApi.createSource(body) : newsApi.updateSource(id, body)
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.newsSources })
    },
  })
}

export function useToggleNewsSource() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (source: NewsSource) => newsApi.setSourceEnabled(source.id, !source.enabled),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.newsSources })
    },
  })
}

export function useDeleteNewsSource() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => newsApi.removeSource(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.newsSources })
      void queryClient.invalidateQueries({ queryKey: queryKeys.news })
    },
  })
}

export function useTrialNewsSource() {
  // 试抓不写库，也不影响任何缓存：结果由调用方就地展示
  return useMutation({ mutationFn: (input: SourceInput) => newsApi.trialSource(input) })
}

// ---------------------------------------------------------------- 采集

export function useCollectNews() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (sourceId?: number) => newsApi.collect(sourceId),
    // 受理即返回；来源健康状态过一会儿才有结果，按前缀刷一次让用户回来能看到
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.newsSources })
    },
  })
}

// ---------------------------------------------------------------- 条目变更

export function useSetNewsRead() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, read }: { id: number; read: boolean }) => newsApi.setRead(id, read),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.news })
    },
  })
}

export function useSetNewsStar() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, starred }: { id: number; starred: boolean }) => newsApi.setStarred(id, starred),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.news })
    },
  })
}

export function useUpdateNewsClassification() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: ClassificationInput }) =>
      newsApi.updateClassification(id, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.news })
    },
  })
}

export function useRegenerateNewsAi() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => newsApi.regenerateAi(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.news })
    },
  })
}

export function useAdoptNewsAi() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: AdoptInput }) => newsApi.adoptAi(id, input),
    onSuccess: () => {
      // 标签采纳可能新建标签，标签缓存一并失效
      void queryClient.invalidateQueries({ queryKey: queryKeys.news })
      void queryClient.invalidateQueries({ queryKey: queryKeys.tags })
    },
  })
}

// ---------------------------------------------------------------- 简报与转任务

/** 简报是「点一次要一次」的临时展示，不落缓存，由调用方管理生命周期。 */
export function useNewsDigest() {
  return useMutation({ mutationFn: () => newsApi.digest() })
}

export function useConvertNewsTask() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: ConvertTaskInput }) =>
      newsApi.convertToTask(id, input),
    onSuccess: () => {
      // 任务进了现有任务体系，任务相关缓存全部刷新
      void queryClient.invalidateQueries({ queryKey: queryKeys.news })
      void queryClient.invalidateQueries({ queryKey: queryKeys.today })
      void queryClient.invalidateQueries({ queryKey: queryKeys.board })
      void queryClient.invalidateQueries({ queryKey: queryKeys.taskLists })
    },
  })
}
