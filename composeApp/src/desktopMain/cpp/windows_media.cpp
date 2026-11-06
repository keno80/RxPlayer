#include <windows.h>
#include <systemmediatransportcontrolsinterop.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Storage.Streams.h>
#include <algorithm>
#include <deque>
#include <memory>
#include <mutex>

using namespace winrt;
using namespace Windows::Media;
using namespace Windows::Storage::Streams;

struct Events { std::mutex mutex; std::deque<int> values; bool closed = false; };
struct Session {
    SystemMediaTransportControls controls{nullptr};
    event_token token{};
    std::shared_ptr<Events> events = std::make_shared<Events>();
    hstring key;
};
#define API extern "C" __declspec(dllexport)

// All exported operations are serialized on the JVM's dedicated WinRT thread.
API int rx_create(void* window, void** result) noexcept {
    *result = nullptr;
    bool initialized = false;
    try {
        init_apartment(apartment_type::multi_threaded);
        initialized = true;
        auto session = std::make_unique<Session>();
        auto interop = get_activation_factory<SystemMediaTransportControls, ISystemMediaTransportControlsInterop>();
        check_hresult(interop->GetForWindow(static_cast<HWND>(window), guid_of<ISystemMediaTransportControls>(), put_abi(session->controls)));
        session->controls.IsEnabled(false);
        auto events = session->events;
        session->token = session->controls.ButtonPressed([events](auto const&, SystemMediaTransportControlsButtonPressedEventArgs const& args) {
            int command = 0;
            switch (args.Button()) {
                case SystemMediaTransportControlsButton::Play: command = 1; break;
                case SystemMediaTransportControlsButton::Pause: command = 2; break;
                case SystemMediaTransportControlsButton::Stop: command = 3; break;
                case SystemMediaTransportControlsButton::Next: command = 4; break;
                case SystemMediaTransportControlsButton::Previous: command = 5; break;
                default: return;
            }
            std::lock_guard<std::mutex> lock(events->mutex);
            if (!events->closed && events->values.size() < 64) events->values.push_back(command);
        });
        *result = session.release();
        return 0;
    } catch (...) { if (initialized) uninit_apartment(); return to_hresult(); }
}

API int rx_update(void* handle, const wchar_t* key, const wchar_t* title, const wchar_t* artist,
    int enabled, int can_skip, int status, long long position_ms, long long duration_ms) noexcept {
    try {
        auto& session = *static_cast<Session*>(handle);
        auto& controls = session.controls;
        controls.IsEnabled(enabled != 0);
        controls.IsPlayEnabled(enabled != 0);
        controls.IsPauseEnabled(enabled != 0);
        controls.IsStopEnabled(enabled != 0);
        controls.IsNextEnabled(can_skip != 0);
        controls.IsPreviousEnabled(can_skip != 0);
        controls.PlaybackStatus(static_cast<MediaPlaybackStatus>(status));
        auto updater = controls.DisplayUpdater();
        if (session.key != hstring(key)) {
            updater.ClearAll();
            updater.Type(MediaPlaybackType::Music);
            updater.MusicProperties().Title(title);
            updater.MusicProperties().Artist(artist);
            updater.Update();
            session.key = key;
        }
        SystemMediaTransportControlsTimelineProperties timeline;
        const auto duration = std::max(0LL, duration_ms);
        timeline.StartTime(Windows::Foundation::TimeSpan{0});
        timeline.EndTime(Windows::Foundation::TimeSpan{duration * 10000});
        timeline.MinSeekTime(Windows::Foundation::TimeSpan{0});
        timeline.MaxSeekTime(Windows::Foundation::TimeSpan{duration * 10000});
        timeline.Position(Windows::Foundation::TimeSpan{std::clamp(position_ms, 0LL, duration) * 10000});
        controls.UpdateTimelineProperties(timeline);
        return 0;
    } catch (...) { return to_hresult(); }
}

API int rx_cover(void* handle, const wchar_t* key, const unsigned char* bytes, int size) noexcept {
    try {
        auto& session = *static_cast<Session*>(handle);
        if (session.key != hstring(key)) return 1;
        if (size <= 0 || size > 4 * 1024 * 1024) return E_INVALIDARG;
        InMemoryRandomAccessStream stream;
        DataWriter writer(stream);
        writer.WriteBytes(array_view<uint8_t const>(bytes, bytes + size));
        writer.StoreAsync().get();
        writer.DetachStream();
        stream.Seek(0);
        auto updater = session.controls.DisplayUpdater();
        updater.Thumbnail(RandomAccessStreamReference::CreateFromStream(stream));
        updater.Update();
        return 0;
    } catch (...) { return to_hresult(); }
}

API int rx_event(void* handle) noexcept {
    auto events = static_cast<Session*>(handle)->events;
    std::lock_guard<std::mutex> lock(events->mutex);
    if (events->values.empty()) return 0;
    int value = events->values.front(); events->values.pop_front(); return value;
}

API int rx_close(void* handle) noexcept {
    std::unique_ptr<Session> session(static_cast<Session*>(handle));
    int result = 0;
    try {
        { std::lock_guard<std::mutex> lock(session->events->mutex); session->events->closed = true; session->events->values.clear(); }
        session->controls.ButtonPressed(session->token);
        session->controls.IsEnabled(false);
        session->controls.DisplayUpdater().ClearAll();
    } catch (...) { result = to_hresult(); }
    session.reset();
    uninit_apartment();
    return result;
}
