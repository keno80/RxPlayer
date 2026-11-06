#include <windows.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
#include <winrt/Windows.Media.Control.h>
#include <iostream>
#include <stdexcept>

using namespace winrt;
using namespace Windows::Media::Control;

int wmain(int argc, wchar_t** argv) {
    if (argc != 2) return 2;
    HWND window = CreateWindowExW(0, L"STATIC", L"RxPlayer SMTC Probe", WS_OVERLAPPEDWINDOW, 0, 0, 320, 200, nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
    HMODULE dll = LoadLibraryW(argv[1]);
    if (!window || !dll) return 3;
    auto create = reinterpret_cast<int(*)(void*,void**)>(GetProcAddress(dll,"rx_create"));
    auto update = reinterpret_cast<int(*)(void*,const wchar_t*,const wchar_t*,const wchar_t*,int,int,int,long long,long long)>(GetProcAddress(dll,"rx_update"));
    auto event = reinterpret_cast<int(*)(void*)>(GetProcAddress(dll,"rx_event"));
    auto close = reinterpret_cast<int(*)(void*)>(GetProcAddress(dll,"rx_close"));
    if (!create || !update || !event || !close) return 4;
    void* handle = nullptr;
    int result = 0;
    try {
        check_hresult(create(window, &handle));
        {
            const auto title = L"RxPlayer SMTC Probe " + std::to_wstring(GetCurrentProcessId());
            check_hresult(update(handle,L"probe",title.c_str(),L"RxPlayer",1,1,3,2000,10000));
            auto manager = GlobalSystemMediaTransportControlsSessionManager::RequestAsync().get();
            GlobalSystemMediaTransportControlsSession target{nullptr};
            for (int attempt=0; attempt<60 && !target; ++attempt) {
                for (auto const& candidate : manager.GetSessions()) {
                    if (candidate.TryGetMediaPropertiesAsync().get().Title() == title) { target=candidate; break; }
                }
                if (!target) Sleep(100);
            }
            if (!target) throw std::runtime_error("SMTC session not visible to Windows manager");
            if (target.TryGetMediaPropertiesAsync().get().Artist() != L"RxPlayer") throw std::runtime_error("Artist mismatch");
            if (!target.TryPauseAsync().get()) throw std::runtime_error("Pause rejected");
            bool paused=false;
            for (int n=0;n<40 && !paused;++n) { paused=event(handle)==2; if(!paused) Sleep(50); }
            if (!paused) throw std::runtime_error("Pause event not delivered");
            check_hresult(update(handle,L"probe",title.c_str(),L"RxPlayer",1,1,4,2000,10000));
            if (!target.TrySkipNextAsync().get()) throw std::runtime_error("Next rejected");
            bool next=false;
            for (int n=0;n<40 && !next;++n) { next=event(handle)==4; if(!next) Sleep(50); }
            if (!next) throw std::runtime_error("Next event not delivered");
            std::cout << "PASS: Windows manager metadata; system pause and next events" << std::endl;
        }
    } catch (hresult_error const& error) { std::cerr << "HRESULT: " << std::hex << error.code().value << std::endl; result=1; }
    catch (std::exception const& error) { std::cerr << error.what() << std::endl; result=1; }
    if(handle) close(handle);
    DestroyWindow(window);
    return result;
}
