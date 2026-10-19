/**
 * 对应后端 com.aiassistant.project.dto.ProjectResponse。
 *
 * 没有 deletedAt：项目按设计只归档不删除，所以「消失」只有 archivedAt 一种表达。
 */
export interface Project {
  id: number
  name: string
  color: string | null
  sortOrder: number
  archivedAt: string | null
}
