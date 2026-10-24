package com.aiassistant.ai;

/**
 * AI 服务的最薄接口：给一段系统指令与用户输入，换回模型输出的文本。
 *
 * <p>抽象成接口是为了让 {@link AiSuggestionService} 的业务规则（开关、限流、结果校验）
 * 可以在不依赖真实 HTTP 的情况下做单元测试；换模型供应商时也只动实现类。
 * 实现负责把「超时 / 不可达 / 返回不可解析」翻译成对应的 AI_* 异常，调用方只管成功路径。
 */
public interface AiClient {

	/**
	 * 发起一次补全调用。返回助手消息的原文（可能是带代码围栏的 JSON），
	 * 解析与字段校验由调用方负责。
	 */
	String complete(String systemPrompt, String userPrompt);
}
