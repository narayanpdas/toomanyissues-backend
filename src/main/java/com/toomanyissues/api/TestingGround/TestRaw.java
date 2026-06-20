package com.toomanyissues.api.TestingGround;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class TestRaw {
    public static void main(String[] args){
        String test = """
                Title:
                Clang 22.1.7 crashes during C++23 module compilation (internal compiler error)
                
                Description: Clang crashes when compiling a C++23 module in an MSYS2 UCRT64 environment. The error is reproducible. The error occurs when attempting to include a class file with the () operator with a large number of specializations.
                
                Environment:
                
                clang++ 22.1.7
                
                Target: x86_64-w64-windows-gnu
                
                MSYS2 UCRT64
                
                Windows 10/11
                Command line:
                ```java
                System.out();
                
                
                <DialogHeader>
                          <DialogTitle gap={4} display="flex" flexDirection="column" alignItems="flex-start">
                            <Button\s
                              size="sm"\s
                              background="orange.800"
                              _hover={{ backgroundColor: "orange.500" }}
                              borderRadius="3xl"
                              onClick={() => {
                                if (issue?.githubIssueId) window.open(issue.repositoryUrl, "_blank");
                              }}
                            >
                              {issue?.repoName} • {getTimeAgo(issue?.createdAtGithub || "")}
                            </Button>
                            <Text fontSize="xl" fontWeight="bold" color="white" lineHeight="tall">
                              {issue?.title}
                            </Text>
                            <Text\s
                              fontSize="xs"\s
                              px={2.5}\s
                              py={1}\s
                              bg={primaryLanguageColorMapper[issue?.primaryLanguage] || "cyan.600"}\s
                              color={getContrastColor(primaryLanguageColorMapper[issue?.primaryLanguage] || "cyan.600")}\s
                              borderRadius="3xl"
                            >
                              {issue?.primaryLanguage}\s
                            </Text>
                          </DialogTitle>
                          <DialogCloseTrigger color="white" />
                        </DialogHeader>
                ```
               ```
               Second Code block
               ```
                
                ********************
                Preprocessed source:
                
                json-e9c0a2.txt Run script: json-e9c0a2.sh
                """;

        final Pattern pattern = Pattern.compile("```(?:[a-zA-Z0-9]+)\\s*?(.*?)```",Pattern.DOTALL);
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(test);
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        System.out.println(matcher.find());
        System.out.println(matches);

//        String x = Arrays.stream(words).limit(100).collect(Collectors.joining(" "));
//        System.out.println(x);
//        System.out.println(Arrays.stream(words)
//                .skip(Math.max(0, words.length - 100))
//                .limit(100).collect(Collectors.joining(" ")));
    }
}
