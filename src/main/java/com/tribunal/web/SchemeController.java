package com.tribunal.web;

import com.tribunal.core.Adjudicator;
import com.tribunal.core.Policy;
import com.tribunal.service.CompareService;
import com.tribunal.service.SchemeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class SchemeController {

    private final SchemeService schemes;
    private final CompareService compare;

    public SchemeController(SchemeService schemes, CompareService compare) {
        this.schemes = schemes;
        this.compare = compare;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("schemes", schemes.listSchemes());
        return "index";
    }

    /** 导入对象（按上传顺序为显式加载顺序）并生成新的不可变方案版本。 */
    @PostMapping("/schemes")
    public String create(@RequestParam("name") String name,
                         @RequestParam("files") List<MultipartFile> files,
                         @RequestParam(defaultValue = "true") boolean copyRelocations,
                         @RequestParam(defaultValue = "false") boolean protectedPreemptible,
                         @RequestParam(defaultValue = "false") boolean tolerateMultipleStrongDefs,
                         @RequestParam(defaultValue = "true") boolean undefinedWeakResolvesToZero) throws IOException {
        Policy policy = new Policy();
        policy.copyRelocations = copyRelocations;
        policy.protectedPreemptible = protectedPreemptible;
        policy.tolerateMultipleStrongDefs = tolerateMultipleStrongDefs;
        policy.undefinedWeakResolvesToZero = undefinedWeakResolvesToZero;
        List<Adjudicator.InputObject> inputs = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f.isEmpty()) continue;
            inputs.add(new Adjudicator.InputObject(f.getOriginalFilename(), f.getBytes()));
        }
        long id = schemes.createScheme(name, policy, inputs);
        return "redirect:/schemes/" + id;
    }

    @GetMapping("/schemes/{id}")
    public String detail(@PathVariable long id, Model model) {
        Map<String, Object> detail = schemes.schemeDetail(id);
        if (detail == null) return "redirect:/";
        model.addAllAttributes(detail);
        model.addAttribute("schemes", schemes.listSchemes());
        return "scheme";
    }

    @GetMapping("/compare")
    public String compare(@RequestParam long a, @RequestParam long b, Model model) {
        model.addAttribute("a", a);
        model.addAttribute("b", b);
        model.addAllAttributes(compare.compare(a, b));
        return "compare";
    }
}
