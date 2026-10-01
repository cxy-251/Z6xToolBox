using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Threading.Tasks;
using Z6xToolBox.App.Framework.Models;

namespace Z6xToolBox.App.Framework.ViewModels;

public sealed class MainWindowViewModel : INotifyPropertyChanged
{
    private readonly Func<string, Task?>? _setClipboard;
    private ModuleDefinition? _selectedModule;
    private string _statusMessage = string.Empty;

    public event PropertyChangedEventHandler? PropertyChanged;

    public IReadOnlyList<ModuleGroup> Groups { get; }

    public ModuleDefinition? SelectedModule
    {
        get => _selectedModule;
        set
        {
            if (value == null) return;
            SetProperty(ref _selectedModule, value);
        }
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public MainWindowViewModel(IReadOnlyList<ModuleDefinition> modules, Func<string, Task?>? setClipboard = null)
    {
        _setClipboard = setClipboard;

        Groups = modules
            .GroupBy(m => m.Group)
            .Select(g => new ModuleGroup
            {
                Title = g.Key,
                Items = g.ToList(),
                IsExpanded = true
            })
            .ToList();

        SelectedModule = modules.FirstOrDefault();
    }

    public async Task CopyToClipboardAsync(string text)
    {
        if (string.IsNullOrEmpty(text) || _setClipboard == null) return;
        try
        {
            var task = _setClipboard(text);
            if (task != null) await task;
            StatusMessage = "已复制到剪贴板";
            _ = Task.Delay(2000).ContinueWith(_ => StatusMessage = string.Empty);
        }
        catch (Exception ex)
        {
            StatusMessage = $"复制失败: {ex.Message}";
        }
    }

    private bool SetProperty<T>(ref T field, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return false;
        field = value;
        OnPropertyChanged(propertyName);
        return true;
    }

    private void OnPropertyChanged([CallerMemberName] string? propertyName = null)
        => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}
