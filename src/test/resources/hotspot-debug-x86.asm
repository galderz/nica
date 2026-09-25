 ;; B22: #    out( B21 B23 ) <- in( B25 B21 ) Loop( B22-B21 inner main of N39 strip mined) Freq: 9.99553e+07
  0x00007fdf4504b0a8:   vmovq        %xmm0, %rsi
  0x00007fdf4504b0ad:   vmovq        0x14(%rsi, %r9), %xmm10
  0x00007fdf4504b0b4:   vmovq        0x14(%rdx, %r9), %xmm0
  0x00007fdf4504b0bb:   vmovq        0x14(%rcx, %r9), %xmm1
  0x00007fdf4504b0c2:   vmovq        0xc(%rsi, %r9), %xmm2
  0x00007fdf4504b0c9:   vmovq        0xc(%rcx, %r9), %xmm3
  0x00007fdf4504b0d0:   vmovq        0xc(%rdx, %r9), %xmm4
  0x00007fdf4504b0d7:   vmovq        0x1c(%rsi, %r9), %xmm5
  0x00007fdf4504b0de:   vmovq        0x1c(%rcx, %r9), %xmm6
  0x00007fdf4504b0e5:   vmovq        0x1c(%rdx, %r9), %xmm7
  0x00007fdf4504b0ec:   vmovq        0x24(%rsi, %r9), %xmm8
  0x00007fdf4504b0f3:   vmovq        0x24(%rcx, %r9), %xmm9
  0x00007fdf4504b0fa:   vmovq        0x24(%rdx, %r9), %xmm12
  0x00007fdf4504b101:   vpmovsxbd        %xmm10, %ymm13
  0x00007fdf4504b106:   vpmovsxbd        %xmm12, %ymm10
  0x00007fdf4504b10b:   vpmovsxbd        %xmm9, %ymm9
  0x00007fdf4504b110:   vpmulld        %ymm9, %ymm10, %ymm12
  0x00007fdf4504b115:   vpaddd        %ymm9, %ymm10, %ymm9
  0x00007fdf4504b11a:   vpmovsxbd        %xmm8, %ymm8
  0x00007fdf4504b11f:   vpmulld        %ymm9, %ymm8, %ymm8
  0x00007fdf4504b124:   vpaddd        %ymm8, %ymm12, %ymm8
  0x00007fdf4504b129:   vpmovsxbd        %xmm7, %ymm7
  0x00007fdf4504b12e:   vpslld        $0x18, %ymm8, %ymm8
  0x00007fdf4504b134:   vpmovsxbd        %xmm6, %ymm6
  0x00007fdf4504b139:   vpaddd        %ymm6, %ymm7, %ymm9
  0x00007fdf4504b13d:   vpmulld        %ymm6, %ymm7, %ymm6
  0x00007fdf4504b142:   vpsrad        $0x18, %ymm8, %ymm7
  0x00007fdf4504b148:   vpmovsxbd        %xmm5, %ymm5
  0x00007fdf4504b14d:   vpmulld        %ymm5, %ymm9, %ymm5
  0x00007fdf4504b152:   vpaddd        %ymm6, %ymm5, %ymm5
  0x00007fdf4504b156:   vpmovsxbd        %xmm4, %ymm4
  0x00007fdf4504b15b:   vpslld        $0x18, %ymm5, %ymm5
  0x00007fdf4504b160:   vpmovsxbd        %xmm3, %ymm3
  0x00007fdf4504b165:   vpaddd        %ymm3, %ymm4, %ymm6
  0x00007fdf4504b169:   vpmulld        %ymm3, %ymm4, %ymm3
  0x00007fdf4504b16e:   vpsrad        $0x18, %ymm5, %ymm4
  0x00007fdf4504b173:   vpmovsxbd        %xmm2, %ymm2
  0x00007fdf4504b178:   vpmulld        %ymm2, %ymm6, %ymm2
  0x00007fdf4504b17d:   vpaddd        %ymm3, %ymm2, %ymm2 ;   {other}
  0x00007fdf4504b181:   vpmovsxbd        %xmm1, %ymm1
  0x00007fdf4504b186:   vpslld        $0x18, %ymm2, %ymm2
  0x00007fdf4504b18b:   vpmovsxbd        %xmm0, %ymm0
  0x00007fdf4504b190:   vpaddd        %ymm0, %ymm1, %ymm3
  0x00007fdf4504b194:   vpmulld        %ymm0, %ymm1, %ymm0
  0x00007fdf4504b199:   vpmulld        %ymm13, %ymm3, %ymm1
  0x00007fdf4504b19e:   vpaddd        %ymm0, %ymm1, %ymm0
  0x00007fdf4504b1a2:   vpsrad        $0x18, %ymm2, %ymm1
  0x00007fdf4504b1a7:   vpxor        %ymm11, %ymm1, %ymm1
  0x00007fdf4504b1ac:   vpslld        $0x18, %ymm0, %ymm0
  0x00007fdf4504b1b1:   vpsrad        $0x18, %ymm0, %ymm0
  0x00007fdf4504b1b6:   vpxor        %ymm0, %ymm1, %ymm0
  0x00007fdf4504b1ba:   vpxor        %ymm0, %ymm4, %ymm0
  0x00007fdf4504b1be:   vpxor        %ymm0, %ymm7, %ymm11
  0x00007fdf4504b1c2:   leal        0x20(%r9), %ebp
  0x00007fdf4504b1c6:   cmpl        %eax, %ebp
  0x00007fdf4504b1c8:   jl        0x7fdf4504b0a0      ;*if_icmpge {reexecute=0 rethrow=0 return_oop=0}
                                                            ; - TestXorByte::testByte@9 (line 20)
