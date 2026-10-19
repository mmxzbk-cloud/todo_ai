import { request } from './client.ts'
import type { Project } from '../types/project.ts'

/** 新建与更新共用一份入参，与后端 ProjectRequest 的三个字段对应。 */
export interface ProjectInput {
  name: string
  /** 不传表示不要颜色（后端落 null），而不是空字符串。 */
  color?: string
}

/**
 * 项目没有 remove：设计上只归档不删除，行内任务的历史归属才不会因为删项目而断掉。
 * includeArchived 为 true 时把归档项目一起取回，设置页与详情页的项目下拉都需要它
 * （否则「任务所属项目已归档」会显示成一个查不到名字的 id）。
 */
export const projectsApi = {
  list: (includeArchived = false): Promise<Project[]> =>
    request<Project[]>(`/api/v1/projects?includeArchived=${String(includeArchived)}`),

  create: (input: ProjectInput): Promise<Project> =>
    request<Project>('/api/v1/projects', { method: 'POST', body: JSON.stringify(input) }),

  update: (id: number, input: ProjectInput): Promise<Project> =>
    request<Project>(`/api/v1/projects/${id}`, { method: 'PUT', body: JSON.stringify(input) }),

  archive: (id: number): Promise<Project> =>
    request<Project>(`/api/v1/projects/${id}/archive`, { method: 'POST' }),

  unarchive: (id: number): Promise<Project> =>
    request<Project>(`/api/v1/projects/${id}/unarchive`, { method: 'POST' }),
}
