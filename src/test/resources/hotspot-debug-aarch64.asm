 ;; B22: #    out( B21 B23 ) <- in( B25 B21 ) Loop( B22-B21 inner main of N37 strip mined) Freq: 9.99553e+07
  0x0000000111e01074:   sxtw        x12, w17            ;*baload {reexecute=0 rethrow=0 return_oop=0}
                                                            ; - TestXorByte::testByte@15 (line 22)
  0x0000000111e01078:   add        x14, x2, x12        ;*baload {reexecute=0 rethrow=0 return_oop=0}
                                                            ; - TestXorByte::testByte@19 (line 22)
  0x0000000111e0107c:   add        x0, x3, x12         ;*baload {reexecute=0 rethrow=0 return_oop=0}
                                                            ; - TestXorByte::testByte@28 (line 22)
  0x0000000111e01080:   ldr        s21, [x14, #0x10]
  0x0000000111e01084:   ldr        s22, [x0, #0x10]
  0x0000000111e01088:   ldr        s23, [x14, #0x18]
  0x0000000111e0108c:   ldr        s24, [x14, #0xc]
  0x0000000111e01090:   ldr        s25, [x0, #0x18]
  0x0000000111e01094:   ldr        s26, [x0, #0xc]
  0x0000000111e01098:   add        x12, x11, x12       ;*baload {reexecute=0 rethrow=0 return_oop=0}
                                                            ; - TestXorByte::testByte@15 (line 22)
  0x0000000111e0109c:   ldr        s16, [x14, #0x14]
  0x0000000111e010a0:   ldr        s17, [x12, #0x10]
  0x0000000111e010a4:   ldr        s18, [x0, #0x14]
  0x0000000111e010a8:   ldr        s19, [x12, #0x18]
  0x0000000111e010ac:   ldr        s20, [x12, #0xc]
  0x0000000111e010b0:   sshll        v26.8h, v26.8b, #0
  0x0000000111e010b4:   sshll        v26.4s, v26.4h, #0
  0x0000000111e010b8:   sshll        v21.8h, v21.8b, #0
  0x0000000111e010bc:   sshll        v21.4s, v21.4h, #0
  0x0000000111e010c0:   sshll        v22.8h, v22.8b, #0
  0x0000000111e010c4:   sshll        v22.4s, v22.4h, #0
  0x0000000111e010c8:   sshll        v23.8h, v23.8b, #0
  0x0000000111e010cc:   sshll        v23.4s, v23.4h, #0
  0x0000000111e010d0:   sshll        v24.8h, v24.8b, #0
  0x0000000111e010d4:   sshll        v24.4s, v24.4h, #0
  0x0000000111e010d8:   sshll        v25.8h, v25.8b, #0
  0x0000000111e010dc:   sshll        v25.4s, v25.4h, #0
  0x0000000111e010e0:   ldr        s30, [x12, #0x14]
  0x0000000111e010e4:   add        v27.4s, v23.4s, v25.4s
  0x0000000111e010e8:   mul        v28.4s, v22.4s, v21.4s
  0x0000000111e010ec:   add        v29.4s, v24.4s, v26.4s
  0x0000000111e010f0:   add        v21.4s, v22.4s, v21.4s
  0x0000000111e010f4:   mul        v22.4s, v24.4s, v26.4s
  0x0000000111e010f8:   sshll        v20.8h, v20.8b, #0
  0x0000000111e010fc:   sshll        v20.4s, v20.4h, #0
  0x0000000111e01100:   sshll        v17.8h, v17.8b, #0
  0x0000000111e01104:   sshll        v17.4s, v17.4h, #0
  0x0000000111e01108:   sshll        v19.8h, v19.8b, #0
  0x0000000111e0110c:   sshll        v19.4s, v19.4h, #0
  0x0000000111e01110:   sshll        v16.8h, v16.8b, #0
  0x0000000111e01114:   sshll        v16.4s, v16.4h, #0
  0x0000000111e01118:   sshll        v18.8h, v18.8b, #0
  0x0000000111e0111c:   sshll        v18.4s, v18.4h, #0
  0x0000000111e01120:   mla        v22.4s, v20.4s, v29.4s
  0x0000000111e01124:   mla        v28.4s, v21.4s, v17.4s
  0x0000000111e01128:   mul        v17.4s, v16.4s, v18.4s
  0x0000000111e0112c:   mul        v19.4s, v19.4s, v27.4s
  0x0000000111e01130:   add        v16.4s, v16.4s, v18.4s
  0x0000000111e01134:   sshll        v18.8h, v30.8b, #0
  0x0000000111e01138:   sshll        v18.4s, v18.4h, #0
  0x0000000111e0113c:   mla        v19.4s, v23.4s, v25.4s
  0x0000000111e01140:   mla        v17.4s, v18.4s, v16.4s
  0x0000000111e01144:   shl        v16.4s, v28.4s, #0x18
  0x0000000111e01148:   shl        v18.4s, v22.4s, #0x18
  0x0000000111e0114c:   shl        v17.4s, v17.4s, #0x18
  0x0000000111e01150:   sshr        v18.4s, v18.4s, #0x18
  0x0000000111e01154:   sshr        v16.4s, v16.4s, #0x18
  0x0000000111e01158:   shl        v19.4s, v19.4s, #0x18
  0x0000000111e0115c:   eor3        v16.16b, v16.16b, v18.16b, v31.16b
  0x0000000111e01160:   sshr        v17.4s, v17.4s, #0x18
  0x0000000111e01164:   sshr        v18.4s, v19.4s, #0x18
  0x0000000111e01168:   eor3        v31.16b, v18.16b, v17.16b, v16.16b
  0x0000000111e0116c:   add        w14, w17, #0x10     ;*iinc {reexecute=0 rethrow=0 return_oop=0}
                                                            ; - TestXorByte::testByte@50 (line 20)
  0x0000000111e01170:   cmp        w14, w13
  0x0000000111e01174:   b.lt        #0x111e01070        ;*if_icmpge {reexecute=0 rethrow=0 return_oop=0}
                                                            ; - TestXorByte::testByte@9 (line 20)
