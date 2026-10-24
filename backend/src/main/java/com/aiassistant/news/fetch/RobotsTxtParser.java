package com.aiassistant.news.fetch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * robots.txt 解析（RFC 9309 的实用子集），纯函数、不碰网络。
 *
 * <p>支持的语法：User-agent 分组（连续的 User-agent 行同组）、Allow/Disallow 前缀规则、
 * {@code *} 通配与 {@code $} 结尾锚点。匹配采用「最长命中优先，长度相同 Allow 胜出」，
 * 与主流爬虫一致。Crawl-delay 与 sitemap 等指令解析但不使用。
 */
public final class RobotsTxtParser {

	private RobotsTxtParser() {
	}

	private record Rule(boolean allow, String pattern) {
	}

	private record Group(List<String> agents, List<Rule> rules) {
	}

	/**
	 * 判断 productToken（如 "AIAssistantNews"）是否可以抓取 path（以 / 开头，可含查询串）。
	 * robots.txt 为 null/空、或没有匹配到任何组/规则时一律允许。
	 */
	public static boolean isAllowed(String robotsTxt, String productToken, String path) {
		List<Group> matched = matchingGroups(parse(robotsTxt), productToken);
		int bestMatchLength = -1;
		boolean allowed = true;
		for (Group group : matched) {
			for (Rule rule : group.rules()) {
				int length = matchLength(rule.pattern(), path);
				if (length < 0) {
					continue;
				}
				if (length > bestMatchLength) {
					bestMatchLength = length;
					allowed = rule.allow();
				} else if (length == bestMatchLength && rule.allow()) {
					allowed = true;
				}
			}
		}
		return allowed;
	}

	private static List<Group> matchingGroups(List<Group> groups, String productToken) {
		String token = productToken.toLowerCase(Locale.ROOT);
		// 有指名道姓匹配本爬虫的组时，通配组不再参与（与 Google 语义一致）
		List<Group> specific = groups.stream()
				.filter(group -> group.agents().stream()
						.anyMatch(agent -> !agent.equals("*") && token.contains(agent.toLowerCase(Locale.ROOT))))
				.toList();
		if (!specific.isEmpty()) {
			return specific;
		}
		return groups.stream().filter(group -> group.agents().contains("*")).toList();
	}

	/** 规则命中时返回命中的长度（越长越优先）；未命中返回 -1。 */
	private static int matchLength(String pattern, String path) {
		if (pattern.isEmpty()) {
			return -1;
		}
		boolean anchored = pattern.endsWith("$");
		String body = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
		StringBuilder regex = new StringBuilder();
		for (int i = 0; i < body.length(); i++) {
			char c = body.charAt(i);
			if (c == '*') {
				regex.append(".*");
			} else {
				regex.append(Pattern.quote(String.valueOf(c)));
			}
		}
		Matcher matcher = Pattern.compile(regex.toString()).matcher(path);
		if (anchored) {
			return matcher.matches() ? path.length() : -1;
		}
		// lookingAt：从头匹配前缀；贪婪的 .* 会给出最长命中
		return matcher.lookingAt() ? matcher.end() : -1;
	}

	private static List<Group> parse(String content) {
		List<Group> groups = new ArrayList<>();
		if (content == null || content.isBlank()) {
			return groups;
		}
		List<String> agents = new ArrayList<>();
		List<Rule> rules = new ArrayList<>();
		boolean sawRule = false;
		for (String rawLine : content.split("\r?\n")) {
			String line = rawLine;
			int comment = line.indexOf('#');
			if (comment >= 0) {
				line = line.substring(0, comment);
			}
			line = line.trim();
			if (line.isEmpty()) {
				continue;
			}
			int colon = line.indexOf(':');
			if (colon <= 0) {
				continue;
			}
			String field = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
			String value = line.substring(colon + 1).trim();
			switch (field) {
				case "user-agent" -> {
					// 规则行之后又出现 User-agent：上一组收起，开启新组（连续的 User-agent 行同组）
					if (sawRule && !agents.isEmpty()) {
						groups.add(new Group(List.copyOf(agents), List.copyOf(rules)));
						agents = new ArrayList<>();
						rules = new ArrayList<>();
					}
					sawRule = false;
					if (!value.isEmpty()) {
						agents.add(value);
					}
				}
				case "allow" -> {
					if (agents.isEmpty()) {
						continue;
					}
					sawRule = true;
					rules.add(new Rule(true, value));
				}
				case "disallow" -> {
					// 空值 Disallow 表示「本组无限制」，不生成规则
					if (agents.isEmpty() || value.isEmpty()) {
						continue;
					}
					sawRule = true;
					rules.add(new Rule(false, value));
				}
				default -> {
					// Crawl-delay / Sitemap 等解析但不用
				}
			}
		}
		if (!agents.isEmpty()) {
			groups.add(new Group(List.copyOf(agents), List.copyOf(rules)));
		}
		return groups;
	}
}
