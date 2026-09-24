long org.sample.handles.VanillaByteArrays::vhandleGetLongLE()  /home/agentuser/src/fibula-show/2609-handles/target/benchmarks [Percent: local period]
   6.36 │      leaq  -0x18(%rsp),%rbx
   7.25 │      cmpq  0x8(%r15),%rbx
        │    → jbe   void com.oracle.svm.core.graal.snippets.StackOverflowCheckImpl::throwNewStackOverflowError()
   6.58 │      movq  %rbx,%rsp
   5.63 │      movl  0x4(%rdi),%eax
   6.20 │      leaq  (%r14,%rax,8),%rcx
   6.46 │      nop
   5.44 │      testl %eax,%eax
        │    ↓ je    43
  12.61 │      movl  0x4(%r14,%rax,8),%edi
   6.04 │      cmpl  $0x8,%edi
        │    ↓ jb    48
   6.33 │      movq  0x8(%r14,%rax,8),%rax
   6.85 │      addq  $0x18,%rsp
   5.75 │      cmpl  $0x0,0x10(%r15)
  12.24 │    → jle   void com.oracle.svm.core.thread.SafepointSlowpath::enterSlowPathSafepointCheckCalleeSavedCCONV()
   6.26 │    ← retq
        │43: → callq void com.oracle.svm.core.snippets.ImplicitExceptions::throwNewNullPointerException()
        │48:   movq  %rcx,0x10(%rsp)
        │      leaq  0x5c7f08(%r14),%rsi
        │      leal  -0x7(%rdi),%edx
        │      movq  %rsi,%rdi
        │      movl  $0x0,%esi
        │      nop
        │    → callq java.lang.RuntimeException* jdk.internal.util.Preconditions::outOfBoundsCheckIndex(java.util.function.BiFunction*, int, int)
        │      leaq  0x20(%rsp),%rsi
        │      movq  %rax,%rdi
        │    → callq void com.oracle.svm.core.snippets.ExceptionUnwind::unwindExceptionWithoutCalleeSavedRegisters(java.lang.Throwable*, org.graalvm.word.Pointer)
        │      nop
