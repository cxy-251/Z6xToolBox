using System.Collections.Generic;

namespace Z6xToolBox.App.Framework.Models;

public sealed class ModuleDefinition
{
    public required string Id { get; init; }
    public required string Title { get; init; }
    public string Group { get; init; } = "设备接入";
    public string Summary { get; init; } = string.Empty;
    public IReadOnlyList<ContentSection> Sections { get; init; } = [];
}

public sealed class ContentSection
{
    public string? Heading { get; init; }
    public string? Text { get; init; }
    public IReadOnlyList<CodeBlock> CodeBlocks { get; init; } = [];
    public IReadOnlyList<string> BulletPoints { get; init; } = [];
}

public sealed class CodeBlock
{
    public string? Label { get; init; }
    public required string Code { get; init; }
    public string? ExpectedOutput { get; init; }
    public string? Note { get; init; }
}
