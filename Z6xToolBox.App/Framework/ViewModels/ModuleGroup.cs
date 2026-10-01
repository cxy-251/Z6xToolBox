using System.Collections.Generic;
using System.ComponentModel;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Framework.ViewModels;

public sealed class ModuleGroup : INotifyPropertyChanged
{
    private bool _isExpanded = true;

    public required string Title { get; init; }
    public required IReadOnlyList<ModuleDefinition> Items { get; init; }

    public bool IsExpanded
    {
        get => _isExpanded;
        set
        {
            if (_isExpanded != value)
            {
                _isExpanded = value;
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(IsExpanded)));
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(ArrowIcon)));
            }
        }
    }

    public string ArrowIcon => _isExpanded ? "▼" : "▶";

    public void Toggle() => IsExpanded = !IsExpanded;

    public event PropertyChangedEventHandler? PropertyChanged;
}
