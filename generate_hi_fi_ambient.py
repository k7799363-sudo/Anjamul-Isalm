import wave
import struct
import math
import random
import os

sample_rate = 44100
duration_sec = 6.0
num_samples = int(sample_rate * duration_sec)

out_dir = "app/src/main/assets/audio/ambient"
os.makedirs(out_dir, exist_ok=True)

def apply_fade(samples, sample_rate, fade_sec=0.1):
    fade_len = int(sample_rate * fade_sec)
    n = len(samples)
    res = list(samples)
    for i in range(fade_len):
        factor = i / float(fade_len)
        res[i] *= factor
        res[n - 1 - i] *= factor
    return res

# 1. Rain: Smooth pink noise with gentle drops
rain_samples_l = []
rain_samples_r = []
b0 = b1 = b2 = b3 = b4 = b5 = b6 = 0.0
b0_r = b1_r = b2_r = b3_r = b4_r = b5_r = b6_r = 0.0

for i in range(num_samples):
    white_l = random.uniform(-1.0, 1.0)
    white_r = random.uniform(-1.0, 1.0)
    
    # Paul Kellet's filter for pink noise
    b0 = 0.99886 * b0 + white_l * 0.0555179
    b1 = 0.99332 * b1 + white_l * 0.0750759
    b2 = 0.96900 * b2 + white_l * 0.1538520
    b3 = 0.86650 * b3 + white_l * 0.3104856
    b4 = 0.55000 * b4 + white_l * 0.5329522
    b5 = -0.7616 * b5 - white_l * 0.0168980
    pink_l = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white_l * 0.5362
    b6 = white_l * 0.115926
    
    b0_r = 0.99886 * b0_r + white_r * 0.0555179
    b1_r = 0.99332 * b1_r + white_r * 0.0750759
    b2_r = 0.96900 * b2_r + white_r * 0.1538520
    b3_r = 0.86650 * b3_r + white_r * 0.3104856
    b4_r = 0.55000 * b4_r + white_r * 0.5329522
    b5_r = -0.7616 * b5_r - white_r * 0.0168980
    pink_r = b0_r + b1_r + b2_r + b3_r + b4_r + b5_r + b6_r + white_r * 0.5362
    b6_r = white_r * 0.115926

    rain_samples_l.append(pink_l * 0.03)
    rain_samples_r.append(pink_r * 0.03)

rain_l = apply_fade(rain_samples_l, sample_rate, 0.1)
rain_r = apply_fade(rain_samples_r, sample_rate, 0.1)

with wave.open(os.path.join(out_dir, "ambient_rain.wav"), "w") as f:
    f.setnchannels(2)
    f.setsampwidth(2)
    f.setframerate(sample_rate)
    for l, r in zip(rain_l, rain_r):
        il = int(max(-1.0, min(1.0, l)) * 32767)
        ir = int(max(-1.0, min(1.0, r)) * 32767)
        f.writeframes(struct.pack("<hh", il, ir))

print("Generated ambient_rain.wav")

# 2. Thunder: Deep brownian rumble
thun_l = []
thun_r = []
last_l = 0.0
last_r = 0.0
for i in range(num_samples):
    t = i / float(sample_rate)
    rumble = math.sin(2 * math.pi * 55 * t) * 0.15 + math.sin(2 * math.pi * 82 * t) * 0.1
    white_l = random.uniform(-1.0, 1.0)
    white_r = random.uniform(-1.0, 1.0)
    last_l = (last_l + (0.02 * white_l)) / 1.02
    last_r = (last_r + (0.02 * white_r)) / 1.02
    thun_l.append((last_l + rumble) * 0.25)
    thun_r.append((last_r + rumble) * 0.25)

thun_l = apply_fade(thun_l, sample_rate, 0.1)
thun_r = apply_fade(thun_r, sample_rate, 0.1)

with wave.open(os.path.join(out_dir, "ambient_thunder.wav"), "w") as f:
    f.setnchannels(2)
    f.setsampwidth(2)
    f.setframerate(sample_rate)
    for l, r in zip(thun_l, thun_r):
        il = int(max(-1.0, min(1.0, l)) * 32767)
        ir = int(max(-1.0, min(1.0, r)) * 32767)
        f.writeframes(struct.pack("<hh", il, ir))

print("Generated ambient_thunder.wav")

# 3. Wind: Soft sweeping breeze
wind_l = []
wind_r = []
last_w_l = 0.0
last_w_r = 0.0
for i in range(num_samples):
    t = i / float(sample_rate)
    sweep = (math.sin(2 * math.pi * 0.2 * t) + 1.0) * 0.5
    white_l = random.uniform(-1.0, 1.0)
    white_r = random.uniform(-1.0, 1.0)
    last_w_l = (last_w_l + (0.015 * white_l)) / 1.015
    last_w_r = (last_w_r + (0.015 * white_r)) / 1.015
    wind_l.append(last_w_l * (0.2 + 0.3 * sweep))
    wind_r.append(last_w_r * (0.2 + 0.3 * sweep))

wind_l = apply_fade(wind_l, sample_rate, 0.1)
wind_r = apply_fade(wind_r, sample_rate, 0.1)

with wave.open(os.path.join(out_dir, "ambient_wind.wav"), "w") as f:
    f.setnchannels(2)
    f.setsampwidth(2)
    f.setframerate(sample_rate)
    for l, r in zip(wind_l, wind_r):
        il = int(max(-1.0, min(1.0, l)) * 32767)
        ir = int(max(-1.0, min(1.0, r)) * 32767)
        f.writeframes(struct.pack("<hh", il, ir))

print("Generated ambient_wind.wav")

# 4. Stream: Flowing water
str_l = []
str_r = []
last_s_l = 0.0
last_s_r = 0.0
for i in range(num_samples):
    t = i / float(sample_rate)
    burb = math.sin(2 * math.pi * 320 * t + math.sin(2 * math.pi * 4 * t)) * 0.02
    white_l = random.uniform(-1.0, 1.0)
    white_r = random.uniform(-1.0, 1.0)
    last_s_l = (last_s_l + (0.04 * white_l)) / 1.04
    last_s_r = (last_s_r + (0.04 * white_r)) / 1.04
    str_l.append((last_s_l + burb) * 0.2)
    str_r.append((last_s_r + burb) * 0.2)

str_l = apply_fade(str_l, sample_rate, 0.1)
str_r = apply_fade(str_r, sample_rate, 0.1)

with wave.open(os.path.join(out_dir, "ambient_stream.wav"), "w") as f:
    f.setnchannels(2)
    f.setsampwidth(2)
    f.setframerate(sample_rate)
    for l, r in zip(str_l, str_r):
        il = int(max(-1.0, min(1.0, l)) * 32767)
        ir = int(max(-1.0, min(1.0, r)) * 32767)
        f.writeframes(struct.pack("<hh", il, ir))

print("Generated ambient_stream.wav")

# 5. Sacred Noor: Warm acoustic drone
noor_l = []
noor_r = []
for i in range(num_samples):
    t = i / float(sample_rate)
    d1 = math.sin(2 * math.pi * 108.0 * t) * 0.12
    d2 = math.sin(2 * math.pi * 162.0 * t) * 0.08
    d3 = math.sin(2 * math.pi * 216.0 * t) * 0.05
    mod = (math.sin(2 * math.pi * 0.1 * t) + 1.0) * 0.5
    sample = (d1 + d2 + d3) * (0.7 + 0.3 * mod)
    noor_l.append(sample)
    noor_r.append(sample)

noor_l = apply_fade(noor_l, sample_rate, 0.1)
noor_r = apply_fade(noor_r, sample_rate, 0.1)

with wave.open(os.path.join(out_dir, "ambient_noor.wav"), "w") as f:
    f.setnchannels(2)
    f.setsampwidth(2)
    f.setframerate(sample_rate)
    for l, r in zip(noor_l, noor_r):
        il = int(max(-1.0, min(1.0, l)) * 32767)
        ir = int(max(-1.0, min(1.0, r)) * 32767)
        f.writeframes(struct.pack("<hh", il, ir))

print("Generated ambient_noor.wav")
