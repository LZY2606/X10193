package com.example.symbolcourt.demo;

import com.example.symbolcourt.domain.Models.InputBinary;
import com.example.symbolcourt.repository.PlanRepository;
import com.example.symbolcourt.service.PlanService;
import com.example.symbolcourt.fixture.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DemoSeeder implements ApplicationRunner {
    private final PlanService planService;
    private final PlanRepository planRepository;

    public DemoSeeder(PlanService planService, PlanRepository planRepository) {
        this.planService = planService;
        this.planRepository = planRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!planRepository.findAll().isEmpty()) {
            return;
        }
        byte[] main = new ElfFixtureBuilder()
                .symbol(SymbolSpec.undef("strong_api", SymbolSpec.Binding.GLOBAL, null))
                .symbol(SymbolSpec.undef("versioned_api", SymbolSpec.Binding.GLOBAL, "LIBV2"))
                .symbol(SymbolSpec.undef("weak_hook", SymbolSpec.Binding.WEAK, null))
                .relocation(0, 1, ElfFixtureBuilder.R_X86_64_PC32, -4)
                .relocation(8, 2, ElfFixtureBuilder.R_X86_64_JUMP_SLOT, 0)
                .relocation(16, 3, ElfFixtureBuilder.R_X86_64_PC32, -4)
                .build();
        byte[] weakProvider = new ElfFixtureBuilder()
                .symbol(SymbolSpec.weakFunc("weak_hook"))
                .build();
        byte[] libV1 = new ElfFixtureBuilder().type(ElfFixtureBuilder.ET_DYN)
                .defineVersion("LIBAPI_1.0", 2, true)
                .symbol(new SymbolSpec("versioned_api", "LIBAPI_1.0", SymbolSpec.Binding.GLOBAL,
                        SymbolSpec.Type.FUNC, SymbolSpec.Visibility.PROTECTED, 1, 0x1000, 8,
                        false, true, true))
                .symbol(SymbolSpec.globalFunc("strong_api"))
                .build();
        byte[] libV2 = new ElfFixtureBuilder().type(ElfFixtureBuilder.ET_DYN)
                .defineVersion("LIBAPI_1.0", 2, true)
                .defineVersion("LIBAPI_2.0", 3, false)
                .symbol(new SymbolSpec("versioned_api", "LIBAPI_2.0", SymbolSpec.Binding.GLOBAL,
                        SymbolSpec.Type.FUNC, SymbolSpec.Visibility.DEFAULT, 1, 0x2000, 8,
                        false, true, true))
                .symbol(new SymbolSpec("versioned_api", "LIBAPI_1.0", SymbolSpec.Binding.GLOBAL,
                        SymbolSpec.Type.FUNC, SymbolSpec.Visibility.DEFAULT, 1, 0x1100, 8,
                        false, true, true))
                .build();
        byte[] archive = new ArchiveFixtureBuilder()
                .member("weak_hook.o", weakProvider)
                .build();
        List<InputBinary> binaries = List.of(
                new InputBinary("demo-main", "main.o", "demo/main.o", main, InputBinary.Kind.ELF),
                new InputBinary("demo-archive", "libhooks.a", "demo/libhooks.a", archive,
                        InputBinary.Kind.ARCHIVE),
                new InputBinary("demo-v1", "libapi-v1.so", "demo/libapi-v1.so", libV1,
                        InputBinary.Kind.ELF),
                new InputBinary("demo-v2", "libapi-v2.so", "demo/libapi-v2.so", libV2,
                        InputBinary.Kind.ELF));
        planService.createFromBinaries("演示方案：版本与 archive 抽取", "GNU", binaries,
                List.of("main.o", "libhooks.a", "libapi-v1.so", "libapi-v2.so"));
        planService.createFromBinaries("演示方案：调换共享库顺序", "GNU", binaries,
                List.of("main.o", "libhooks.a", "libapi-v2.so", "libapi-v1.so"));
    }
}
