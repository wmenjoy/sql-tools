package com.footstone.sqlguard.scanner.validator;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.ViolationInfo;
import org.dom4j.Element;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis Mapper XML 安全验证器
 * 
 * <p>直接在 XML 层面进行安全检查，而不是试图生成完整的 SQL。
 * 这个验证器理解 MyBatis 的语义，可以准确检测动态 SQL 中的安全问题。</p>
 * 
 * <p>检查项目：</p>
 * <ul>
 *   <li>SQL 注入风险：检测 ${} 的使用</li>
 *   <li>缺少 WHERE 子句：DELETE/UPDATE 没有 WHERE 条件</li>
 *   <li>SELECT * 使用：性能问题</li>
 *   <li>敏感表访问：访问敏感数据表</li>
 * </ul>
 */
public class MyBatisMapperValidator {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisMapperValidator.class);

    // 检查开关（默认值）
    private boolean checkSelectStar = false;  // SELECT * 检查默认关闭
    private boolean checkSensitiveTables = false;  // 敏感表检查默认关闭
    private boolean checkDynamicWhereClause = false;  // 动态 WHERE 条件检查默认关闭
    private boolean checkOrderByInjection = false;  // ORDER BY 注入检查默认关闭
    private boolean checkLimitOffsetInjection = false;  // LIMIT/OFFSET 注入检查默认关闭
    private boolean checkAggregateFunctionInjection = false;  // 聚合函数动态字段检查默认关闭

    // 敏感表名列表（可配置）
    private static final List<String> SENSITIVE_TABLES = Arrays.asList(
        "user", "users", "password", "passwords", "admin", "admins",
        "role", "roles", "permission", "permissions", "auth", "token"
    );
    
    // 限制性字段模式（可配置）
    // 默认包含常见的 ID 字段模式
    private List<String> limitingFieldPatterns = Arrays.asList(
        "id",           // 单独的 id 字段
        "[a-z_]+_id",   // 以 _id 结尾的字段（如 user_id, developer_id）
        "[a-z]+Id",     // 驼峰命名的 Id 字段（如 userId, developerId）
        "uuid",         // UUID 字段
        "primary_key",  // 主键字段
        "pk"            // 主键缩写
    );
    
    // 缓存编译后的 Pattern（当配置改变时重新编译）
    private Pattern idFieldPattern = null;
    
    // 表白名单：这些表不需要检查物理分页（例如：配置表、字典表、小数据量表）
    private List<String> tableWhitelist = new ArrayList<>();
    
    // 表黑名单：这些表必须检查物理分页（优先级高于白名单）
    private List<String> tableBlacklist = new ArrayList<>();

    // SQL 注入模式：${...}
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("\\$\\{[^}]+\\}");

    // SELECT * 模式
    private static final Pattern SELECT_STAR_PATTERN = Pattern.compile(
        "\\bselect\\s+\\*\\s+from\\b", 
        Pattern.CASE_INSENSITIVE
    );

    // 表名提取模式
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile(
        "\\b(from|join|into|update)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 设置是否检查 SELECT *
     */
    public void setCheckSelectStar(boolean checkSelectStar) {
        this.checkSelectStar = checkSelectStar;
    }

    /**
     * 设置是否检查敏感表
     */
    public void setCheckSensitiveTables(boolean checkSensitiveTables) {
        this.checkSensitiveTables = checkSensitiveTables;
    }

    /**
     * 设置是否检查动态 WHERE 条件
     */
    public void setCheckDynamicWhereClause(boolean checkDynamicWhereClause) {
        this.checkDynamicWhereClause = checkDynamicWhereClause;
    }

    /**
     * 设置是否检查 ORDER BY 注入
     */
    public void setCheckOrderByInjection(boolean checkOrderByInjection) {
        this.checkOrderByInjection = checkOrderByInjection;
    }

    /**
     * 设置是否检查 LIMIT/OFFSET 注入
     */
    public void setCheckLimitOffsetInjection(boolean checkLimitOffsetInjection) {
        this.checkLimitOffsetInjection = checkLimitOffsetInjection;
    }

    /**
     * 设置是否检查聚合函数动态字段注入
     */
    public void setCheckAggregateFunctionInjection(boolean checkAggregateFunctionInjection) {
        this.checkAggregateFunctionInjection = checkAggregateFunctionInjection;
    }

    /**
     * 设置限制性字段模式
     * 这些字段在 WHERE 条件中出现时，会被认为可以限制结果集大小，从而不需要物理分页
     * 
     * @param patterns 正则表达式模式列表，例如：["id", "user_id", "[a-z]+_id"]
     */
    public void setLimitingFieldPatterns(List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            throw new IllegalArgumentException("Limiting field patterns cannot be null or empty");
        }
        this.limitingFieldPatterns = new ArrayList<>(patterns);
        // 清除缓存的 Pattern，下次使用时重新编译
        this.idFieldPattern = null;
    }

    /**
     * 设置表白名单
     * 白名单中的表不需要检查物理分页（例如：配置表、字典表、小数据量表）
     * 
     * @param whitelist 表名列表，例如：["sys_config", "dict_type", "api_version_plugins"]
     */
    public void setTableWhitelist(List<String> whitelist) {
        this.tableWhitelist = whitelist != null ? new ArrayList<>(whitelist) : new ArrayList<>();
    }

    /**
     * 设置表黑名单
     * 黑名单中的表必须检查物理分页，优先级高于白名单
     * 
     * @param blacklist 表名列表，例如：["user", "order", "log"]
     */
    public void setTableBlacklist(List<String> blacklist) {
        this.tableBlacklist = blacklist != null ? new ArrayList<>(blacklist) : new ArrayList<>();
    }
    
    /**
     * 获取或编译限制性字段的 Pattern
     * 使用延迟初始化和缓存机制
     * 支持 = 和 IN 操作符
     */
    private Pattern getIdFieldPattern() {
        if (idFieldPattern == null) {
            // 将所有模式组合成一个正则表达式
            // 格式：\b(pattern1|pattern2|pattern3)\s*(=|IN)
            // 匹配：id = #{id} 或 group_id IN (...)
            String combinedPattern = "\\b(" + String.join("|", limitingFieldPatterns) + ")\\s*(=|IN)";
            idFieldPattern = Pattern.compile(combinedPattern, Pattern.CASE_INSENSITIVE);
        }
        return idFieldPattern;
    }

    /**
     * 验证 MyBatis Mapper XML 元素的安全性
     *
     * @param sqlElement SQL 语句元素（select, insert, update, delete）
     * @param mapperId Mapper ID（用于日志）
     * @return 检测到的安全违规列表
     */
    public List<ViolationInfo> validate(Element sqlElement, String mapperId) {
        List<ViolationInfo> violations = new ArrayList<>();

        if (sqlElement == null) {
            return violations;
        }

        String tagName = sqlElement.getName();
        logger.debug("Validating MyBatis mapper element: {} ({})", mapperId, tagName);

        // 核心检查 1: SQL 注入风险（最高优先级）
        violations.addAll(checkSqlInjection(sqlElement, mapperId));

        // 核心检查 2: WHERE 条件缺失或永真（DELETE/UPDATE/SELECT）
        if ("delete".equals(tagName) || "update".equals(tagName)) {
            violations.addAll(checkWhereCondition(sqlElement, tagName));
        } else if ("select".equals(tagName)) {
            violations.addAll(checkSelectWhereAndPagination(sqlElement, mapperId));
            
            // 可选检查：SELECT * 使用（默认关闭）
            if (checkSelectStar) {
                violations.addAll(checkSelectStarUsage(sqlElement));
            }
        }

        // 可选检查：敏感表访问（默认关闭）
        if (checkSensitiveTables) {
            violations.addAll(checkSensitiveTables(sqlElement));
        }

        logger.debug("MyBatis validation completed for {}: {} violations found", 
            mapperId, violations.size());

        return violations;
    }

    /**
     * 检查 SQL 注入风险：递归扫描所有文本节点，查找 ${} 模式
     */
    @SuppressWarnings("unchecked")
    private List<ViolationInfo> checkSqlInjection(Element element, String mapperId) {
        List<ViolationInfo> violations = new ArrayList<>();

        // 获取当前元素的所有文本内容（包括子元素）
        String allText = getAllTextContent(element);

        // 查找所有 ${} 使用
        Matcher matcher = SQL_INJECTION_PATTERN.matcher(allText);
        while (matcher.find()) {
            String placeholder = matcher.group();
            
            // 检查是否为 ORDER BY 场景，且开关关闭
            if (isOrderByPlaceholder(placeholder, allText) && !checkOrderByInjection) {
                logger.debug("Skipping ORDER BY injection check for {}: {} (check disabled)", 
                    mapperId, placeholder);
                continue;
            }
            
            // 检查是否为 LIMIT/OFFSET 场景
            if (isLimitOffsetPlaceholder(placeholder, allText)) {
                // LIMIT/OFFSET 有特殊处理：即使关闭注入检查，也要检查参数合理性
                if (!checkLimitOffsetInjection) {
                    // 注入检查关闭，但仍然检查参数合理性
                    violations.addAll(checkLimitOffsetParameters(element, placeholder, allText));
                    logger.debug("Skipping LIMIT/OFFSET injection check for {}: {} (check disabled, but validating parameters)", 
                        mapperId, placeholder);
                    continue;
                } else {
                    // 注入检查开启，报告注入风险 + 参数合理性
                    String suggestion = getSmartSuggestion(placeholder, allText);
                    violations.add(new ViolationInfo(
                        RiskLevel.CRITICAL,
                        String.format("SQL 注入风险 - 检测到 %s 占位符", placeholder),
                        suggestion
                    ));
                    violations.addAll(checkLimitOffsetParameters(element, placeholder, allText));
                    logger.warn("SQL injection risk detected in {}: {}", mapperId, placeholder);
                    continue;
                }
            }
            
            // 检查是否为聚合函数动态字段场景，且开关关闭
            if (isAggregateFunctionPlaceholder(placeholder, allText) && !checkAggregateFunctionInjection) {
                logger.debug("Skipping aggregate function injection check for {}: {} (check disabled)", 
                    mapperId, placeholder);
                continue;
            }
            
            String suggestion = getSmartSuggestion(placeholder, allText);
            
            violations.add(new ViolationInfo(
                RiskLevel.CRITICAL,
                String.format("SQL 注入风险 - 检测到 %s 占位符", placeholder),
                suggestion
            ));
            
            logger.warn("SQL injection risk detected in {}: {}", mapperId, placeholder);
        }

        return violations;
    }
    
    /**
     * 检查是否为 ORDER BY 占位符
     */
    private boolean isOrderByPlaceholder(String placeholder, String sqlText) {
        String lowerSql = sqlText.toLowerCase();
        String placeholderName = placeholder.replaceAll("[\\${}]", "").toLowerCase();
        
        // 检查是否在 ORDER BY 上下文中
        boolean inOrderByContext = lowerSql.contains("order by");
        
        // 检查占位符名称是否与排序相关
        boolean isOrderRelatedName = placeholderName.contains("order") || 
                                     placeholderName.contains("sort") ||
                                     placeholderName.equals("orderby") ||
                                     placeholderName.equals("orderbyclause");
        
        return inOrderByContext && isOrderRelatedName;
    }
    
    /**
     * 检查是否为 LIMIT/OFFSET 占位符
     */
    private boolean isLimitOffsetPlaceholder(String placeholder, String sqlText) {
        String lowerSql = sqlText.toLowerCase();
        String placeholderName = placeholder.replaceAll("[\\${}]", "").toLowerCase();
        
        // 检查是否在 LIMIT 上下文中
        boolean inLimitContext = lowerSql.contains("limit");
        
        // 检查占位符名称是否与分页相关
        boolean isLimitRelatedName = placeholderName.contains("limit") || 
                                     placeholderName.contains("offset") ||
                                     placeholderName.contains("pagesize") ||
                                     placeholderName.contains("page");
        
        return inLimitContext && isLimitRelatedName;
    }
    
    /**
     * 检查是否为聚合函数动态字段占位符
     */
    private boolean isAggregateFunctionPlaceholder(String placeholder, String sqlText) {
        String lowerSql = sqlText.toLowerCase();
        String placeholderName = placeholder.replaceAll("[\\${}]", "").toLowerCase();
        
        // 检查是否在聚合函数上下文中
        boolean inAggregateContext = lowerSql.contains("sum(") || 
                                     lowerSql.contains("avg(") ||
                                     lowerSql.contains("count(") ||
                                     lowerSql.contains("max(") ||
                                     lowerSql.contains("min(");
        
        // 检查占位符名称是否与聚合字段相关
        boolean isAggregateRelatedName = placeholderName.contains("col") || 
                                         placeholderName.contains("column") ||
                                         placeholderName.contains("field") ||
                                         placeholderName.contains("sum") ||
                                         placeholderName.contains("avg");
        
        return inAggregateContext && isAggregateRelatedName;
    }
    
    /**
     * 检查 LIMIT/OFFSET 参数的合理性
     */
    @SuppressWarnings("unchecked")
    private List<ViolationInfo> checkLimitOffsetParameters(Element element, String placeholder, String sqlText) {
        List<ViolationInfo> violations = new ArrayList<>();
        String placeholderName = placeholder.replaceAll("[\\${}]", "");
        
        // 检查 1: 参数可能为空
        // 查找包含该占位符的 <if> 标签
        List<Element> ifTags = element.elements("if");
        boolean hasNullCheck = false;
        
        for (Element ifTag : ifTags) {
            String testAttr = ifTag.attributeValue("test");
            String ifContent = getAllTextContent(ifTag).toLowerCase();
            
            // 检查这个 <if> 是否包含我们的占位符，并且有 null 检查
            if (ifContent.contains(placeholder.toLowerCase()) && 
                testAttr != null && 
                (testAttr.contains(placeholderName) && testAttr.contains("!= null"))) {
                hasNullCheck = true;
                break;
            }
        }
        
        if (hasNullCheck) {
            // 有 null 检查，但参数可能为 null 时分页失效，并且需要验证参数范围
            violations.add(new ViolationInfo(
                RiskLevel.MEDIUM,
                String.format("LIMIT 分页参数 %s 可能为空或值过大", placeholder),
                "建议：1) 在业务层保证分页参数不能为空；" +
                "2) 限制参数范围（例如：offset < 10000, limit <= 1000）；" +
                "3) 使用 MyBatis RowBounds 或 PageHelper 实现物理分页"
            ));
        }
        
        return violations;
    }

    /**
     * 根据上下文生成智能建议
     */
    private String getSmartSuggestion(String placeholder, String sqlText) {
        String lowerSql = sqlText.toLowerCase();
        String placeholderName = placeholder.replaceAll("[\\${}]", "");
        
        // ORDER BY 场景
        if (lowerSql.contains("order by") && 
            (placeholderName.toLowerCase().contains("order") || 
             placeholderName.toLowerCase().contains("sort"))) {
            return "建议方案：1) XML 白名单验证 <choose><when test=\"orderBy == 'id'\">id</when>" +
                   "<when test=\"orderBy == 'name'\">name</when><otherwise>id</otherwise></choose>；" +
                   "2) 业务层白名单验证 Set.of(\"id\", \"name\", \"create_time\")";
        }
        
        // LIMIT/OFFSET 场景
        if ((lowerSql.contains("limit") || lowerSql.contains("offset")) &&
            (placeholderName.toLowerCase().contains("limit") || 
             placeholderName.toLowerCase().contains("offset"))) {
            return "建议方案：1) 使用 MyBatis RowBounds: mapper.selectByExample(example, new RowBounds(offset, limit))；" +
                   "2) 使用 PageHelper: PageHelper.startPage(pageNum, pageSize)";
        }
        
        // IN 子句场景
        String lowerPlaceholderName = placeholderName.toLowerCase();
        if (lowerSql.contains(" in ") && 
            (lowerPlaceholderName.contains("id") || 
             lowerPlaceholderName.contains("ids") ||
             lowerPlaceholderName.contains("list") ||
             lowerPlaceholderName.contains("array"))) {
            return "【IN 子句动态值】\n" +
                   "   问题：IN (${ids}) 存在 SQL 注入风险\n" +
                   "   正确方案：使用 <foreach> 标签\n" +
                   "   示例：<foreach collection=\"ids\" item=\"id\" open=\"(\" close=\")\" separator=\",\">\n" +
                   "           #{id}\n" +
                   "         </foreach>\n" +
                   "   注意：这样每个值都会被参数化，安全且高效";
        }
        
        // 聚合函数场景（SUM, AVG, COUNT 等）
        if ((lowerSql.contains("sum(") || lowerSql.contains("avg(") || 
             lowerSql.contains("count(") || lowerSql.contains("max(") || 
             lowerSql.contains("min(")) &&
            (lowerPlaceholderName.contains("col") || 
             lowerPlaceholderName.contains("column") ||
             lowerPlaceholderName.contains("field"))) {
            return "建议：使用白名单验证 <choose><when test=\"col == 'amount'\">amount</when>" +
                   "<when test=\"col == 'count'\">count</when><otherwise>id</otherwise></choose>";
        }
        
        // UPDATE SET 场景（检查占位符名称中包含 update 或 sql）
        if (lowerSql.contains("update") &&
            (lowerPlaceholderName.contains("updatesql") || 
             lowerPlaceholderName.contains("update_sql") ||
             lowerPlaceholderName.contains("setsql") ||
             lowerPlaceholderName.contains("set_sql"))) {
            return "【UPDATE 动态 SQL】\n" +
                   "   问题：直接拼接 SQL 片段存在注入风险\n" +
                   "   方案1：使用 <set> + <if> 标签单独处理每个字段\n" +
                   "   方案2：使用 updateByPrimaryKeySelective() 方法\n" +
                   "   方案3：业务层白名单验证允许的字段名";
        }
        
        // 表名/列名场景
        if (placeholderName.toLowerCase().contains("table") || 
            placeholderName.toLowerCase().contains("column")) {
            return "【动态表名/列名】\n" +
                   "   严重警告：禁止使用动态表名/列名！\n" +
                   "   建议：为每个表创建独立的 Mapper 方法\n" +
                   "   如必须使用：业务层严格白名单验证（Set.of(\"table1\", \"table2\")）";
        }
        
        // 通用场景
        return "【SQL 注入风险】\n" +
               "   注意：不能简单地将 ${} 替换为 #{}！\n" +
               "   • ORDER BY：使用 <choose> 标签白名单\n" +
               "   • LIMIT：使用 RowBounds 或 PageHelper\n" +
               "   • 动态字段：使用 <if> 标签或业务层白名单\n" +
               "   • 动态表名：禁止使用或严格白名单验证";
    }

    /**
     * 检查 DELETE/UPDATE 是否缺少 WHERE 子句
     */
    @SuppressWarnings("unchecked")
    private List<ViolationInfo> checkMissingWhereClause(Element element, String tagName) {
        List<ViolationInfo> violations = new ArrayList<>();

        // 检查是否有 <where> 标签
        boolean hasWhereTag = !element.elements("where").isEmpty();

        // 检查文本中是否包含 WHERE 关键字
        String allText = getAllTextContent(element).toLowerCase();
        boolean hasWhereKeyword = allText.contains("where");

        // 如果既没有 <where> 标签，也没有 WHERE 关键字
        if (!hasWhereTag && !hasWhereKeyword) {
            violations.add(new ViolationInfo(
                RiskLevel.HIGH,
                String.format("%s without WHERE clause", tagName.toUpperCase()),
                "Add WHERE condition to prevent accidental data modification"
            ));
        }

        return violations;
    }

    /**
     * 检查 SELECT * 的使用（可选检查，默认关闭）
     */
    private List<ViolationInfo> checkSelectStarUsage(Element element) {
        List<ViolationInfo> violations = new ArrayList<>();

        String allText = getAllTextContent(element);
        
        Matcher matcher = SELECT_STAR_PATTERN.matcher(allText);
        if (matcher.find()) {
            violations.add(new ViolationInfo(
                RiskLevel.LOW,
                "SELECT * 查询 - 可能影响性能",
                "建议：明确指定需要的列名，提升查询性能和可维护性"
            ));
        }

        return violations;
    }

    /**
     * 检查是否访问敏感表
     */
    private List<ViolationInfo> checkSensitiveTables(Element element) {
        List<ViolationInfo> violations = new ArrayList<>();

        String allText = getAllTextContent(element);
        
        // 提取所有表名
        Matcher matcher = TABLE_NAME_PATTERN.matcher(allText);
        while (matcher.find()) {
            String tableName = matcher.group(2).toLowerCase();
            
            if (SENSITIVE_TABLES.contains(tableName)) {
                violations.add(new ViolationInfo(
                    RiskLevel.MEDIUM,
                    String.format("Accessing sensitive table: %s", tableName),
                    "Ensure proper access control and data masking are in place"
                ));
            }
        }

        return violations;
    }

    /**
     * 递归获取元素及其所有子元素的文本内容
     */
    @SuppressWarnings("unchecked")
    private String getAllTextContent(Element element) {
        StringBuilder sb = new StringBuilder();

        // 遍历所有节点（包括文本节点和元素节点）
        List<Node> content = element.content();
        for (Node node : content) {
            if (node.getNodeType() == Node.TEXT_NODE || 
                node.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(node.getText()).append(" ");
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                // 递归处理子元素
                sb.append(getAllTextContent((Element) node)).append(" ");
            }
        }

        return sb.toString();
    }

    /**
     * 核心检查：WHERE 条件缺失或永真（针对 DELETE/UPDATE）
     */
    @SuppressWarnings("unchecked")
    private List<ViolationInfo> checkWhereCondition(Element element, String tagName) {
        List<ViolationInfo> violations = new ArrayList<>();

        // 检查是否有 <where> 标签或 <if> 包含 WHERE
        List<Element> whereTags = element.elements("where");
        List<Element> ifTags = element.elements("if");
        
        // 检查文本中是否包含 WHERE 关键字
        String allText = getAllTextContent(element).toLowerCase();
        boolean hasWhereKeyword = allText.contains("where");
        
        // 检查是否有 <include> 引用 WHERE 子句
        boolean hasWhereInclude = checkHasWhereInclude(element);

        if (whereTags.isEmpty() && !hasWhereKeyword && !hasWhereInclude) {
            // 完全没有 WHERE
            violations.add(new ViolationInfo(
                RiskLevel.CRITICAL,
                String.format("%s 语句缺少 WHERE 条件 - 将影响全表数据", tagName.toUpperCase()),
                "必须添加 WHERE 条件以防止误操作全表数据"
            ));
        } else if (hasWhereInclude || (!ifTags.isEmpty() && allText.contains("_parameter"))) {
            // 有 <include> 或动态条件，需要进一步分析
            
            // 检查是否为 MyBatis Generator 标准模式（有 _parameter != null 保护）
            boolean isSafeGeneratedPattern = isSafeMyBatisGeneratorPattern(element, ifTags);
            
            // 只有在开启动态 WHERE 检查时，且不是安全的生成模式时，才报告
            if (checkDynamicWhereClause && !isSafeGeneratedPattern) {
                violations.add(new ViolationInfo(
                    RiskLevel.HIGH,
                    String.format("%s 语句的 WHERE 条件可能为空 - 当条件参数为 null 时将影响全表", tagName.toUpperCase()),
                    "建议：1) 在业务层保证查询条件参数不能为空；2) 使用 <where> 标签并确保至少有一个必填条件"
                ));
            }
        } else if (allText.contains("1=1") || allText.contains("1 = 1")) {
            // 检查永真条件
            boolean hasRealCondition = checkHasRealCondition(element, allText);
            
            if (!hasRealCondition) {
                violations.add(new ViolationInfo(
                    RiskLevel.CRITICAL,
                    String.format("%s 语句只有永真条件 (1=1) - 将影响全表数据", tagName.toUpperCase()),
                    "必须添加真实的 WHERE 条件，或删除该语句（如果确实需要全表操作）"
                ));
            } else {
                violations.add(new ViolationInfo(
                    RiskLevel.HIGH,
                    String.format("%s 语句使用了 1=1 - 当动态条件全部为空时将变成永真条件", tagName.toUpperCase()),
                    "建议：1) 确保动态条件不能全部为空；2) 使用 <where> 标签自动移除 1=1"
                ));
            }
        }

        return violations;
    }
    
    /**
     * 检查是否为安全的 MyBatis Generator 生成模式
     * 特征：<if test="_parameter != null"><include refid="Example_Where_Clause"/></if>
     * 或：<if test="_parameter != null"><include refid="Update_By_Example_Where_Clause"/></if>
     */
    @SuppressWarnings("unchecked")
    private boolean isSafeMyBatisGeneratorPattern(Element element, List<Element> ifTags) {
        for (Element ifTag : ifTags) {
            String testAttr = ifTag.attributeValue("test");
            if (testAttr != null && testAttr.contains("_parameter") && testAttr.contains("!= null")) {
                // 检查 <if> 标签内是否有 <include> 引用标准 WHERE 子句
                List<Element> includes = ifTag.elements("include");
                for (Element include : includes) {
                    String refid = include.attributeValue("refid");
                    if (refid != null && 
                        (refid.contains("Where_Clause") || 
                         refid.equals("Example_Where_Clause") ||
                         refid.equals("Update_By_Example_Where_Clause"))) {
                        // 这是 MyBatis Generator 的标准模式
                        // _parameter != null 确保了 Example 对象不为空
                        // Example_Where_Clause 会根据 Example 中的条件生成 WHERE
                        // 如果 Example 有条件，WHERE 就有效；如果没有条件，WHERE 就不会生成
                        // 这是一个安全的设计模式
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * 检查是否有 <include> 引用 WHERE 子句（递归检查所有子元素）
     */
    @SuppressWarnings("unchecked")
    private boolean checkHasWhereInclude(Element element) {
        // 检查当前元素的 <include>
        List<Element> includes = element.elements("include");
        for (Element include : includes) {
            String refid = include.attributeValue("refid");
            if (refid != null && (refid.contains("Where") || refid.contains("WHERE") || refid.contains("where"))) {
                return true;
            }
        }
        
        // 递归检查所有子元素（如 <if>, <choose> 等）
        List<Element> children = element.elements();
        for (Element child : children) {
            if (checkHasWhereInclude(child)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 核心检查：SELECT 的 WHERE 条件和分页（物理 vs 逻辑）
     */
    @SuppressWarnings("unchecked")
    private List<ViolationInfo> checkSelectWhereAndPagination(Element element, String mapperId) {
        List<ViolationInfo> violations = new ArrayList<>();

        String allText = getAllTextContent(element).toLowerCase();
        
        // 特殊处理：聚合查询（SUM、COUNT、AVG、MAX、MIN）通常不需要 WHERE 条件
        boolean isAggregateQuery = isAggregateQuery(allText);
        
        // 检查 1: WHERE 条件（聚合查询除外）
        if (!isAggregateQuery) {
            boolean hasWhereTag = !element.elements("where").isEmpty();
            boolean hasWhereKeyword = allText.contains("where");
            boolean hasWhereInclude = checkHasWhereInclude(element);
            List<Element> ifTags = element.elements("if");
            
            if (!hasWhereTag && !hasWhereKeyword && !hasWhereInclude) {
                violations.add(new ViolationInfo(
                    RiskLevel.MEDIUM,
                    "SELECT 查询缺少 WHERE 条件 - 将查询全表数据",
                    "建议：添加 WHERE 条件限制结果集，或使用物理分页"
                ));
            } else if (hasWhereInclude || (!ifTags.isEmpty() && allText.contains("_parameter"))) {
                // 检查是否为安全的 MyBatis Generator 模式
                boolean isSafeGeneratedPattern = isSafeMyBatisGeneratorPattern(element, ifTags);
                
                // 只有在开启动态 WHERE 检查时，且不是安全的生成模式时，才报告
                if (checkDynamicWhereClause && !isSafeGeneratedPattern) {
                    violations.add(new ViolationInfo(
                        RiskLevel.MEDIUM,
                        "SELECT 查询的 WHERE 条件可能为空 - 当条件参数为 null 时将查询全表",
                        "建议：在业务层保证查询条件参数不能为空"
                    ));
                }
            } else if (allText.contains("1=1") || allText.contains("1 = 1")) {
                boolean hasRealCondition = checkHasRealCondition(element, allText);
                if (!hasRealCondition) {
                    violations.add(new ViolationInfo(
                        RiskLevel.MEDIUM,
                        "SELECT 查询只有永真条件 (1=1) - 将查询全表数据",
                        "建议：添加真实的 WHERE 条件限制结果集"
                    ));
                }
            }
        }

        // 检查 2: 分页机制（物理分页 vs 逻辑分页）
        // 聚合查询通常返回单行结果，不需要分页
        if (!isAggregateQuery) {
            // 检查表是否在白名单/黑名单中
            boolean shouldCheck = shouldCheckPagination(allText);
            
            if (shouldCheck) {
                boolean hasPhysicalPagination = checkPhysicalPagination(element, allText, mapperId);
                
                if (!hasPhysicalPagination) {
                    // 检查是否有限制结果集的条件（如主键、唯一索引）
                    boolean hasLimitingCondition = hasLimitingWhereCondition(allText);
                    
                    if (!hasLimitingCondition) {
                        // 没有物理分页且没有限制条件 - 可能是逻辑分页（危险）
                        violations.add(new ViolationInfo(
                            RiskLevel.HIGH,
                            "SELECT 查询缺少物理分页 - 大数据量时可能导致内存溢出（逻辑分页风险）",
                            "建议：使用物理分页机制 - LIMIT 子句、RowBounds 参数、PageHelper 或 MyBatis-Plus 的 IPage/Page"
                        ));
                    }
                }
            }
        }

        return violations;
    }
    
    /**
     * 检查是否为聚合查询（SUM、COUNT、AVG、MAX、MIN）
     */
    private boolean isAggregateQuery(String sqlText) {
        // 提取 SELECT 和 FROM 之间的内容
        int selectIdx = sqlText.indexOf("select");
        int fromIdx = sqlText.indexOf("from");
        
        if (selectIdx == -1 || fromIdx == -1 || selectIdx >= fromIdx) {
            return false;
        }
        
        String selectClause = sqlText.substring(selectIdx + 6, fromIdx).trim();
        
        // 检查是否包含聚合函数
        return selectClause.matches(".*\\b(sum|count|avg|max|min)\\s*\\(.*")
            && !selectClause.contains(",");  // 排除混合查询（如 SELECT id, COUNT(*) ...）
    }
    
    /**
     * 检查表是否需要分页检查
     * 
     * @param sqlText SQL 文本
     * @return true 表示需要检查分页，false 表示豁免检查
     */
    private boolean shouldCheckPagination(String sqlText) {
        // 提取表名
        String tableName = extractTableName(sqlText);
        if (tableName == null || tableName.isEmpty()) {
            return true; // 无法提取表名，默认检查
        }
        
        // 黑名单优先级最高：如果在黑名单中，必须检查
        if (tableBlacklist != null && !tableBlacklist.isEmpty()) {
            for (String blackTable : tableBlacklist) {
                if (tableName.equalsIgnoreCase(blackTable)) {
                    return true; // 在黑名单中，必须检查
                }
            }
        }
        
        // 白名单：如果在白名单中，豁免检查
        if (tableWhitelist != null && !tableWhitelist.isEmpty()) {
            for (String whiteTable : tableWhitelist) {
                if (tableName.equalsIgnoreCase(whiteTable)) {
                    return false; // 在白名单中，豁免检查
                }
            }
        }
        
        // 默认需要检查
        return true;
    }
    
    /**
     * 从 SQL 文本中提取表名
     */
    private String extractTableName(String sqlText) {
        Matcher matcher = TABLE_NAME_PATTERN.matcher(sqlText);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }
    
    /**
     * 检查 WHERE 条件中是否包含可能限制结果集的字段
     * 例如：主键、唯一索引、ID 字段等
     */
    private boolean hasLimitingWhereCondition(String sqlText) {
        // 使用动态配置的正则表达式匹配限制性字段
        // 匹配模式：id =, user_id =, userId =, developer_id =, uuid = 等
        Pattern pattern = getIdFieldPattern();
        Matcher matcher = pattern.matcher(sqlText);
        return matcher.find();
    }

    /**
     * 检查是否有真实的条件（不只是 1=1）
     */
    private boolean checkHasRealCondition(Element element, String allText) {
        // 检查是否有 <if> 标签（动态条件）
        if (!element.elements("if").isEmpty()) {
            return true;
        }
        
        // 检查是否有 #{} 参数（实际条件）
        if (allText.contains("#{")) {
            // 排除只在 1=1 之前的情况
            String whereClause = allText.substring(allText.indexOf("where"));
            String withoutTrueCondition = whereClause.replaceAll("1\\s*=\\s*1", "")
                                                     .replaceAll("'1'\\s*=\\s*'1'", "")
                                                     .replaceAll("true", "");
            return withoutTrueCondition.contains("#{");
        }
        
        return false;
    }

    /**
     * 检查是否使用了物理分页
     */
    private boolean checkPhysicalPagination(Element element, String allText, String mapperId) {
        // 1. 检查 LIMIT 子句（MySQL/PostgreSQL）
        if (allText.contains("limit")) {
            return true;
        }
        
        // 2. 检查 OFFSET/FETCH（SQL Server/Oracle）
        if (allText.contains("offset") && allText.contains("fetch")) {
            return true;
        }
        
        // 3. 检查 ROWNUM（Oracle）
        if (allText.contains("rownum")) {
            return true;
        }
        
        // 4. 检查方法名是否包含 RowBounds（需要在 Java 接口中检查，这里只能通过方法名推断）
        if (mapperId.toLowerCase().contains("rowbound")) {
            return true;
        }
        
        // 5. 检查方法名是否包含 Page（PageHelper 或 MyBatis-Plus）
        if (mapperId.toLowerCase().contains("page") && 
            !mapperId.toLowerCase().contains("update") &&
            !mapperId.toLowerCase().contains("delete")) {
            return true;
        }
        
        // 注意：真正的 RowBounds/IPage/Page 参数检查需要在语义分析中完成
        // 这里只是基于 XML 的初步检查
        
        return false;
    }
}


