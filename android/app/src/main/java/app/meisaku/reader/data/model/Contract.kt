package app.meisaku.reader.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 数据契约 v1 的客户端模型。对应 Document/DATA-CONTRACT.md。
 * catalog 下的 json 用紧凑键；book doc 的 run 以「键存在」区分类型，故用 DTO 解析后转域模型。
 */

@Serializable
data class Meta(
    val version: Int = 1,
    val edition: String = "curated",
    val bookCount: Int = 0,
    val authorCount: Int = 0,
    val schema: String = "v1",
    val generatedAt: String = "",
)

@Serializable
data class Author(
    val id: String,
    val name: String,
    val kana: String? = null,
    val roman: String? = null,
    val birth: String? = null,
    val death: String? = null,
    val count: Int = 0,
)

/** catalog/books.json 单条（紧凑键）。 */
@Serializable
data class BookSummary(
    val id: String,
    @SerialName("t") val title: String,
    @SerialName("tk") val titleKana: String? = null,
    @SerialName("a") val authorId: String,
    @SerialName("c") val category: String? = null,
)

// --- books/{id}.json 解析用 DTO ---

@Serializable
internal data class BookDocDto(
    val id: String,
    val title: String,
    val author: String,
    val authorId: String? = null,
    val blocks: List<BlockDto> = emptyList(),
)

@Serializable
internal data class BlockDto(
    val k: String,
    val level: Int? = null,
    val r: List<RunDto> = emptyList(),
)

@Serializable
internal data class RunDto(
    val t: String? = null,
    val b: String? = null,
    val f: String? = null,
    val g: String? = null,
    val d: String? = null,
    val n: String? = null,
)

// --- 域模型（渲染用） ---

sealed interface Run {
    /** 普通文本 */
    data class Text(val text: String) : Run
    /** 振假名：base + furigana */
    data class Ruby(val base: String, val furigana: String) : Run
    /** 无法还原的外字：占位符 + 描述 */
    data class Gaiji(val placeholder: String, val desc: String?) : Run
    /** 行内注记（保留项） */
    data class Note(val note: String) : Run
}

enum class BlockKind { Paragraph, Heading, Blank }

data class Block(
    val kind: BlockKind,
    val level: Int = 0,
    val runs: List<Run> = emptyList(),
)

data class BookDoc(
    val id: String,
    val title: String,
    val author: String,
    val authorId: String?,
    val blocks: List<Block>,
)

internal fun BookDocDto.toDomain(): BookDoc = BookDoc(
    id = id,
    title = title,
    author = author,
    authorId = authorId,
    blocks = blocks.mapNotNull { it.toDomain() },
)

private fun BlockDto.toDomain(): Block? {
    val kind = when (k) {
        "p" -> BlockKind.Paragraph
        "h" -> BlockKind.Heading
        "blank" -> BlockKind.Blank
        else -> return null
    }
    if (kind == BlockKind.Blank) return Block(BlockKind.Blank)
    val runs = r.mapNotNull { it.toDomain() }
    return Block(kind, level ?: 0, runs)
}

private fun RunDto.toDomain(): Run? = when {
    t != null -> Run.Text(t)
    b != null -> Run.Ruby(b, f ?: "")
    g != null -> Run.Gaiji(g, d)
    n != null -> Run.Note(n)
    else -> null
}
