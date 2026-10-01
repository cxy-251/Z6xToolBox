using System;
using Avalonia;

namespace Z6xToolBox.Desktop;

internal static class Program
{
    [STAThread]
    public static void Main(string[] args) => BuildAvaloniaApp()
        .StartWithClassicDesktopLifetime(args);

    public static AppBuilder BuildAvaloniaApp()
        => AppBuilder.Configure<Z6xToolBox.App.App>()
            .UsePlatformDetect()
            .WithInterFont()
            .LogToTrace();
}
