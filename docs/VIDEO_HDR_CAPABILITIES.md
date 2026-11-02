# Video and HDR

Rendering goes MediaCodec → SurfaceView, so HDR10/HLG/Dolby Vision are shown when the decoder, Fire OS output mode and TV all support them; the app does no tone mapping. Frame-rate matching uses Media3's default strategy. Tunneling is off (can improve sync/HDR on some sticks, but changes subtitle behaviour and needs per-device testing). `Best available` quality mode lifts the 1080p cap; unsupported variants are excluded by Media3's own decoder capability check. Nothing is upscaled. 8K and HDR10+ are reported from decoder/display flags only, never claimed.
