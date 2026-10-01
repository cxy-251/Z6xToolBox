using Avalonia.Controls;
using Avalonia.Interactivity;
using Z6xToolBox.App.Framework.ViewModels;

namespace Z6xToolBox.App.Framework.Views;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
    }

    private async void OnCopyButtonClick(object? sender, RoutedEventArgs e)
    {
        if (sender is Button { Tag: string command } && DataContext is MainWindowViewModel vm)
        {
            await vm.CopyToClipboardAsync(command);
        }
    }

    private void OnGroupHeaderClick(object? sender, RoutedEventArgs e)
    {
        if (sender is Button { Tag: ModuleGroup group })
        {
            group.Toggle();
        }
    }

    private void OnListBoxSelectionChanged(object? sender, SelectionChangedEventArgs e)
    {
        if (e.AddedItems.Count > 0 && e.AddedItems[0] is Z6xToolBox.App.Framework.Models.ModuleDefinition mod)
        {
            if (DataContext is MainWindowViewModel vm && vm.SelectedModule != mod)
            {
                vm.SelectedModule = mod;
            }
        }
    }
}
