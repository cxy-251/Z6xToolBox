using Avalonia;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Markup.Xaml;
using Z6xToolBox.App.Content;
using Z6xToolBox.App.Framework.ViewModels;
using Z6xToolBox.App.Framework.Views;

namespace Z6xToolBox.App;

public partial class App : Application
{
    public override void Initialize()
    {
        AvaloniaXamlLoader.Load(this);
    }

    public override void OnFrameworkInitializationCompleted()
    {
        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            var mainWindow = new MainWindow();
            var viewModel = new MainWindowViewModel(
                ContentRegistry.GetAllModules(),
                async text =>
                {
                    if (mainWindow.Clipboard != null)
                    {
                        await mainWindow.Clipboard.SetTextAsync(text);
                    }
                });
            mainWindow.DataContext = viewModel;
            desktop.MainWindow = mainWindow;
        }

        base.OnFrameworkInitializationCompleted();
    }
}
