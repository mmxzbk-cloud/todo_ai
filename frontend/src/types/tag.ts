/**
 * 对应后端 com.aiassistant.tag.dto.TagResponse。
 *
 * 单独放一个文件而不是塞进 types/task.ts：types/task.ts 的 TaskDetail 要引用 Tag，
 * 而 Tag 本身也归标签设置页用，两个任务的文件靠这个独立模块解耦（字段名三个必须一致）。
 */
export interface Tag {
  id: number
  name: string
  color: string | null
}
