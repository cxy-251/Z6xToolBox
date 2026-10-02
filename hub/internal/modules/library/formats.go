package library

import (
	"bytes"
	"os"
	"regexp"
	"strconv"
	"strings"
	"unicode/utf8"

	"golang.org/x/text/encoding/simplifiedchinese"
	"golang.org/x/text/encoding/traditionalchinese"
)

// 小说与文档的其他格式：TXT（按「第 N 章」分章）、Markdown 与 reStructuredText（技术文档）。
// MD、RST 转换为「块」（标题、段落、代码、列表、引用），由网页用 textContent 逐块显示，
// 不生成 HTML，因此文档中的任何标签都不会被执行；行内的粗体、链接等只保留文字。

// block 是文档中的一块内容。
type block struct {
	Kind  string `json:"k"`           // h、p、code、li、quote、hr
	Text  string `json:"t,omitempty"` //
	Level int    `json:"l,omitempty"` // 标题级别
}

// decodeText 按 UTF-8、GB18030、Big5 的顺序尝试解码（与 omni-deck 相同），都不行时按 UTF-8 忽略错误。
func decodeText(b []byte) string {
	b = bytes.TrimPrefix(b, []byte("\xef\xbb\xbf"))
	if utf8.Valid(b) {
		return string(b)
	}
	for _, dec := range [][]byte{
		mustDecode(simplifiedchinese.GB18030.NewDecoder().Bytes(b)),
		mustDecode(traditionalchinese.Big5.NewDecoder().Bytes(b)),
	} {
		if dec != nil {
			return string(dec)
		}
	}
	return strings.ToValidUTF8(string(b), "")
}

func mustDecode(b []byte, err error) []byte {
	if err != nil {
		return nil
	}
	return b
}

var txtChapter = regexp.MustCompile(`(?im)^[ \t\x{3000}]*(第\s*[0-9一二三四五六七八九十百千万零〇两]+\s*[章回节卷集部篇][^\n]{0,50}|Chapter\s+[0-9]+[^\n]{0,50})\s*$`)

// parseTXT 按章节标题分章；找不到章节标题时，每 300 段为一部分，避免整本书一次显示。
func parseTXT(p string) ([]novelChapter, error) {
	raw, err := os.ReadFile(p)
	if err != nil {
		return nil, err
	}
	text := strings.ReplaceAll(decodeText(raw), "\r\n", "\n")
	locs := txtChapter.FindAllStringSubmatchIndex(text, -1)
	var out []novelChapter
	add := func(title, body string) {
		if paras := textLines(body); len(paras) > 0 {
			out = append(out, novelChapter{Title: title, Paras: paras})
		}
	}
	if len(locs) >= 2 {
		add("序", text[:locs[0][0]])
		for i, l := range locs {
			end := len(text)
			if i+1 < len(locs) {
				end = locs[i+1][0]
			}
			add(strings.TrimSpace(text[l[2]:l[3]]), text[l[1]:end])
		}
		return out, nil
	}
	lines := textLines(text)
	for i := 0; i < len(lines); i += 300 {
		end := min(i+300, len(lines))
		out = append(out, novelChapter{Title: "第 " + strconv.Itoa(i/300+1) + " 部分", Paras: lines[i:end]})
	}
	return out, nil
}

func textLines(s string) []string {
	var out []string
	for _, line := range strings.Split(s, "\n") {
		if line = strings.TrimSpace(strings.ReplaceAll(line, "　", " ")); line != "" {
			out = append(out, line)
		}
	}
	return out
}

var (
	mdHeading = regexp.MustCompile(`^(#{1,6})\s+(.*?)\s*#*\s*$`)
	mdList    = regexp.MustCompile(`^\s*([-*+]|\d+[.)])\s+(.*)$`)
	mdHR      = regexp.MustCompile(`^\s*([-*_])(\s*[-*_]){2,}\s*$`)
	mdLink    = regexp.MustCompile(`!?\[([^\]]*)\]\([^)]*\)`)
	mdEmph    = regexp.MustCompile("(\\*\\*|__|\\*|`)")
	rstRole   = regexp.MustCompile(":[a-z:]+:`([^`]*)`")
	rstLink   = regexp.MustCompile("`([^`<]*?)\\s*<[^>]*>`_+")
)

// isUnderline 判断是否为 RST 标题的下划线：至少 3 个相同的标点符号（Go 的正则不支持反向引用，故单独判断）。
func isUnderline(line string) bool {
	t := strings.TrimRight(line, " \t")
	if len(t) < 3 || !strings.ContainsRune("=-~^\"'+#*`:.", rune(t[0])) {
		return false
	}
	return strings.Count(t, t[:1]) == len(t)
}

// inline 去掉行内标记，只保留文字。
func inline(s string, rst bool) string {
	if rst {
		s = rstRole.ReplaceAllString(s, "$1")
		s = rstLink.ReplaceAllString(s, "$1")
		return strings.NewReplacer("``", "", "**", "", "`_", "", "`", "").Replace(s)
	}
	return mdEmph.ReplaceAllString(mdLink.ReplaceAllString(s, "$1"), "")
}

var fmTitle = regexp.MustCompile(`(?m)^title:\s*["']?(.*?)["']?\s*$`)

// frontMatter 去掉 Markdown 开头的 YAML 元数据（两行 --- 之间），返回其中的 title 与正文。
// 不去掉时，元数据会显示为两条分隔线夹一段文字，并成为空的第一章（2026-10-02 用真实文档测出）。
func frontMatter(text string) (title, body string) {
	text = strings.ReplaceAll(text, "\r\n", "\n")
	if !strings.HasPrefix(text, "---\n") {
		return "", text
	}
	end := strings.Index(text[4:], "\n---")
	if end < 0 {
		return "", text
	}
	meta := text[4 : 4+end]
	rest := text[4+end+4:]
	if i := strings.IndexByte(rest, '\n'); i >= 0 {
		rest = rest[i+1:]
	} else {
		rest = ""
	}
	if m := fmTitle.FindStringSubmatch(meta); m != nil {
		title = m[1]
	}
	return title, rest
}

// parseMarkdown 把 Markdown 转换为块（开头的 YAML 元数据应先用 frontMatter 去掉）。
func parseMarkdown(text string) []block {
	var out []block
	var para []string
	flush := func() {
		if len(para) > 0 {
			out = append(out, block{Kind: "p", Text: inline(strings.Join(para, " "), false)})
			para = nil
		}
	}
	lines := strings.Split(strings.ReplaceAll(text, "\r\n", "\n"), "\n")
	for i := 0; i < len(lines); i++ {
		line := lines[i]
		trim := strings.TrimSpace(line)
		switch {
		case strings.HasPrefix(trim, "```") || strings.HasPrefix(trim, "~~~"):
			flush()
			fence := trim[:3]
			var code []string
			for i++; i < len(lines) && !strings.HasPrefix(strings.TrimSpace(lines[i]), fence); i++ {
				code = append(code, lines[i])
			}
			out = append(out, block{Kind: "code", Text: strings.Join(code, "\n")})
		case trim == "":
			flush()
		case mdHeading.MatchString(trim):
			flush()
			m := mdHeading.FindStringSubmatch(trim)
			out = append(out, block{Kind: "h", Level: len(m[1]), Text: inline(m[2], false)})
		case mdHR.MatchString(trim):
			flush()
			out = append(out, block{Kind: "hr"})
		case strings.HasPrefix(trim, ">"):
			flush()
			out = append(out, block{Kind: "quote", Text: inline(strings.TrimSpace(strings.TrimLeft(trim, ">")), false)})
		case mdList.MatchString(line):
			flush()
			out = append(out, block{Kind: "li", Text: inline(mdList.FindStringSubmatch(line)[2], false)})
		case strings.HasPrefix(line, "    ") || strings.HasPrefix(line, "\t"):
			flush()
			code := []string{strings.TrimPrefix(strings.TrimPrefix(line, "    "), "\t")}
			for i+1 < len(lines) && (strings.HasPrefix(lines[i+1], "    ") || strings.HasPrefix(lines[i+1], "\t") || strings.TrimSpace(lines[i+1]) == "") {
				i++
				code = append(code, strings.TrimPrefix(strings.TrimPrefix(lines[i], "    "), "\t"))
			}
			out = append(out, block{Kind: "code", Text: strings.TrimRight(strings.Join(code, "\n"), "\n")})
		case strings.HasPrefix(trim, "|"):
			flush() // 表格按原样作为等宽文本显示
			tbl := []string{trim}
			for i+1 < len(lines) && strings.HasPrefix(strings.TrimSpace(lines[i+1]), "|") {
				i++
				tbl = append(tbl, strings.TrimSpace(lines[i]))
			}
			out = append(out, block{Kind: "code", Text: strings.Join(tbl, "\n")})
		default:
			para = append(para, trim)
		}
	}
	flush()
	return out
}

// parseRST 把 reStructuredText 转换为块：下划线标题（按出现顺序确定级别）、`::` 后的缩进代码块、
// code-block 指令、列表；其他指令（.. xxx::）连同其缩进内容略去，注释同样略去。
func parseRST(text string) []block {
	var out []block
	var para []string
	levels := map[string]int{}
	flush := func() {
		if len(para) > 0 {
			s := inline(strings.Join(para, " "), true)
			if strings.HasSuffix(s, "::") {
				s = strings.TrimSuffix(s, ":")
			}
			out = append(out, block{Kind: "p", Text: s})
			para = nil
		}
	}
	lines := strings.Split(strings.ReplaceAll(text, "\r\n", "\n"), "\n")
	indented := func(i int) []string { // 读取第 i 行起的缩进块
		var code []string
		for ; i < len(lines) && (strings.TrimSpace(lines[i]) == "" || strings.HasPrefix(lines[i], " ") || strings.HasPrefix(lines[i], "\t")); i++ {
			code = append(code, lines[i])
		}
		return code
	}
	dedent := func(code []string) string {
		minIndent := -1
		for _, l := range code {
			if strings.TrimSpace(l) == "" {
				continue
			}
			n := len(l) - len(strings.TrimLeft(l, " \t"))
			if minIndent < 0 || n < minIndent {
				minIndent = n
			}
		}
		for k, l := range code {
			if len(l) >= minIndent && minIndent > 0 {
				code[k] = l[minIndent:]
			}
		}
		return strings.Trim(strings.Join(code, "\n"), "\n")
	}
	for i := 0; i < len(lines); i++ {
		line := lines[i]
		trim := strings.TrimSpace(line)
		// 标题：文字行下面紧跟一行由同一符号组成、长度不短于文字的下划线（上划线形式也兼容）
		if trim != "" && i+1 < len(lines) && isUnderline(lines[i+1]) && !isUnderline(line) &&
			utf8.RuneCountInString(strings.TrimSpace(lines[i+1])) >= min(utf8.RuneCountInString(trim), 3) {
			flush()
			ch := lines[i+1][:1]
			if _, ok := levels[ch]; !ok {
				levels[ch] = len(levels) + 1
			}
			out = append(out, block{Kind: "h", Level: levels[ch], Text: inline(trim, true)})
			i++
			continue
		}
		if isUnderline(line) {
			continue // 上划线
		}
		switch {
		case trim == "":
			flush()
		case strings.HasPrefix(trim, ".. code-block::") || strings.HasPrefix(trim, ".. code::") || strings.HasPrefix(trim, ".. sourcecode::"):
			flush()
			code := indented(i + 1)
			i += len(code)
			var body []string
			for _, l := range code { // 跳过指令选项（:linenos: 等）
				if !strings.HasPrefix(strings.TrimSpace(l), ":") || len(body) > 0 {
					body = append(body, l)
				}
			}
			out = append(out, block{Kind: "code", Text: dedent(body)})
		case strings.HasPrefix(trim, ".. "):
			flush()
			i += len(indented(i + 1)) // 其他指令与注释整体略去
		case strings.HasSuffix(trim, "::") && i+1 < len(lines):
			para = append(para, trim)
			flush()
			code := indented(i + 1)
			i += len(code)
			if c := dedent(code); c != "" {
				out = append(out, block{Kind: "code", Text: c})
			}
		case mdList.MatchString(line):
			flush()
			out = append(out, block{Kind: "li", Text: inline(mdList.FindStringSubmatch(line)[2], true)})
		default:
			para = append(para, trim)
		}
	}
	flush()
	return out
}

// splitDoc 按标题把文档分成若干章，便于用目录跳转：取文档中出现的最高两级标题中较低的一级
// （常见结构是一个总标题加若干二级标题），没有标题时整篇为一章。
func splitDoc(title string, blocks []block) []novelChapter {
	top, count := 99, map[int]int{}
	for _, b := range blocks {
		if b.Kind == "h" {
			count[b.Level]++
			top = min(top, b.Level)
		}
	}
	cut := top
	if count[top] == 1 && count[top+1] > 0 {
		cut = top + 1
	}
	var out []novelChapter
	cur := novelChapter{Title: title}
	for _, b := range blocks {
		if b.Kind == "h" && b.Level <= cut && hasBody(cur.Blocks) {
			out = append(out, cur)
			cur = novelChapter{Title: b.Text}
		} else if b.Kind == "h" && b.Level <= cut {
			cur.Title = b.Text // 只有标题、没有正文的部分（如总标题）并入下一章，不单独成章
		}
		cur.Blocks = append(cur.Blocks, b)
	}
	if len(cur.Blocks) > 0 {
		out = append(out, cur)
	}
	return out
}

func hasBody(blocks []block) bool {
	for _, b := range blocks {
		if b.Kind != "h" && b.Kind != "hr" {
			return true
		}
	}
	return false
}
