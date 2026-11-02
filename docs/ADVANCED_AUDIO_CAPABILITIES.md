# Advanced audio

Distinguish: (A) bitstream passthrough, (B) licensed decoding, (C) platform/OEM processing, (D) software multichannel decode, (E) virtual surround. This app does **A** (via Media3 when the HDMI sink reports the format) and relies on platform decoders for **C**. It ships no **B**, no **D** for Dolby/DTS, and no **E**. Multichannel PCM/AAC/FLAC is delivered to the sink as Android allows; downmix happens in the platform when the output cannot take the layout.

Not done: audio delay, DSD, hi-res/bit-perfect claims, DTS:X/Atmos badges, channel-identification test files (need real media and a receiver). Use the capability report to see what the current route (HDMI/ARC/Bluetooth/USB) advertises.
