/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * Dynamic texts inside one another: a text that runs the next one from a session variable answers from two hundred
 * and one texts deep, past the hundred this engine once stopped at, and a block among them counts as a call — blocks
 * that each run the next through EXECUTE IMMEDIATE are refused at the sixteenth, {@code Stored procedure execution
 * error: Stored procedure exceeds the maximum allowed number of recursive calls.}, which the block before it catches
 * (live-verified).
 */
public class NestedDynamicTextTest extends MeasuredCellsTestSupport {

    @Test
    public void textsNestPastAHundred() {
        engine.execute("SET t0 = 'SELECT ''bottom'''");
        engine.execute("SET t1 = 'EXECUTE IMMEDIATE $t0'");
        engine.execute("SET t2 = 'EXECUTE IMMEDIATE $t1'");
        engine.execute("SET t3 = 'EXECUTE IMMEDIATE $t2'");
        engine.execute("SET t4 = 'EXECUTE IMMEDIATE $t3'");
        engine.execute("SET t5 = 'EXECUTE IMMEDIATE $t4'");
        engine.execute("SET t6 = 'EXECUTE IMMEDIATE $t5'");
        engine.execute("SET t7 = 'EXECUTE IMMEDIATE $t6'");
        engine.execute("SET t8 = 'EXECUTE IMMEDIATE $t7'");
        engine.execute("SET t9 = 'EXECUTE IMMEDIATE $t8'");
        engine.execute("SET t10 = 'EXECUTE IMMEDIATE $t9'");
        engine.execute("SET t11 = 'EXECUTE IMMEDIATE $t10'");
        engine.execute("SET t12 = 'EXECUTE IMMEDIATE $t11'");
        engine.execute("SET t13 = 'EXECUTE IMMEDIATE $t12'");
        engine.execute("SET t14 = 'EXECUTE IMMEDIATE $t13'");
        engine.execute("SET t15 = 'EXECUTE IMMEDIATE $t14'");
        engine.execute("SET t16 = 'EXECUTE IMMEDIATE $t15'");
        engine.execute("SET t17 = 'EXECUTE IMMEDIATE $t16'");
        engine.execute("SET t18 = 'EXECUTE IMMEDIATE $t17'");
        engine.execute("SET t19 = 'EXECUTE IMMEDIATE $t18'");
        engine.execute("SET t20 = 'EXECUTE IMMEDIATE $t19'");
        engine.execute("SET t21 = 'EXECUTE IMMEDIATE $t20'");
        engine.execute("SET t22 = 'EXECUTE IMMEDIATE $t21'");
        engine.execute("SET t23 = 'EXECUTE IMMEDIATE $t22'");
        engine.execute("SET t24 = 'EXECUTE IMMEDIATE $t23'");
        engine.execute("SET t25 = 'EXECUTE IMMEDIATE $t24'");
        engine.execute("SET t26 = 'EXECUTE IMMEDIATE $t25'");
        engine.execute("SET t27 = 'EXECUTE IMMEDIATE $t26'");
        engine.execute("SET t28 = 'EXECUTE IMMEDIATE $t27'");
        engine.execute("SET t29 = 'EXECUTE IMMEDIATE $t28'");
        engine.execute("SET t30 = 'EXECUTE IMMEDIATE $t29'");
        engine.execute("SET t31 = 'EXECUTE IMMEDIATE $t30'");
        engine.execute("SET t32 = 'EXECUTE IMMEDIATE $t31'");
        engine.execute("SET t33 = 'EXECUTE IMMEDIATE $t32'");
        engine.execute("SET t34 = 'EXECUTE IMMEDIATE $t33'");
        engine.execute("SET t35 = 'EXECUTE IMMEDIATE $t34'");
        engine.execute("SET t36 = 'EXECUTE IMMEDIATE $t35'");
        engine.execute("SET t37 = 'EXECUTE IMMEDIATE $t36'");
        engine.execute("SET t38 = 'EXECUTE IMMEDIATE $t37'");
        engine.execute("SET t39 = 'EXECUTE IMMEDIATE $t38'");
        engine.execute("SET t40 = 'EXECUTE IMMEDIATE $t39'");
        engine.execute("SET t41 = 'EXECUTE IMMEDIATE $t40'");
        engine.execute("SET t42 = 'EXECUTE IMMEDIATE $t41'");
        engine.execute("SET t43 = 'EXECUTE IMMEDIATE $t42'");
        engine.execute("SET t44 = 'EXECUTE IMMEDIATE $t43'");
        engine.execute("SET t45 = 'EXECUTE IMMEDIATE $t44'");
        engine.execute("SET t46 = 'EXECUTE IMMEDIATE $t45'");
        engine.execute("SET t47 = 'EXECUTE IMMEDIATE $t46'");
        engine.execute("SET t48 = 'EXECUTE IMMEDIATE $t47'");
        engine.execute("SET t49 = 'EXECUTE IMMEDIATE $t48'");
        engine.execute("SET t50 = 'EXECUTE IMMEDIATE $t49'");
        engine.execute("SET t51 = 'EXECUTE IMMEDIATE $t50'");
        engine.execute("SET t52 = 'EXECUTE IMMEDIATE $t51'");
        engine.execute("SET t53 = 'EXECUTE IMMEDIATE $t52'");
        engine.execute("SET t54 = 'EXECUTE IMMEDIATE $t53'");
        engine.execute("SET t55 = 'EXECUTE IMMEDIATE $t54'");
        engine.execute("SET t56 = 'EXECUTE IMMEDIATE $t55'");
        engine.execute("SET t57 = 'EXECUTE IMMEDIATE $t56'");
        engine.execute("SET t58 = 'EXECUTE IMMEDIATE $t57'");
        engine.execute("SET t59 = 'EXECUTE IMMEDIATE $t58'");
        engine.execute("SET t60 = 'EXECUTE IMMEDIATE $t59'");
        engine.execute("SET t61 = 'EXECUTE IMMEDIATE $t60'");
        engine.execute("SET t62 = 'EXECUTE IMMEDIATE $t61'");
        engine.execute("SET t63 = 'EXECUTE IMMEDIATE $t62'");
        engine.execute("SET t64 = 'EXECUTE IMMEDIATE $t63'");
        engine.execute("SET t65 = 'EXECUTE IMMEDIATE $t64'");
        engine.execute("SET t66 = 'EXECUTE IMMEDIATE $t65'");
        engine.execute("SET t67 = 'EXECUTE IMMEDIATE $t66'");
        engine.execute("SET t68 = 'EXECUTE IMMEDIATE $t67'");
        engine.execute("SET t69 = 'EXECUTE IMMEDIATE $t68'");
        engine.execute("SET t70 = 'EXECUTE IMMEDIATE $t69'");
        engine.execute("SET t71 = 'EXECUTE IMMEDIATE $t70'");
        engine.execute("SET t72 = 'EXECUTE IMMEDIATE $t71'");
        engine.execute("SET t73 = 'EXECUTE IMMEDIATE $t72'");
        engine.execute("SET t74 = 'EXECUTE IMMEDIATE $t73'");
        engine.execute("SET t75 = 'EXECUTE IMMEDIATE $t74'");
        engine.execute("SET t76 = 'EXECUTE IMMEDIATE $t75'");
        engine.execute("SET t77 = 'EXECUTE IMMEDIATE $t76'");
        engine.execute("SET t78 = 'EXECUTE IMMEDIATE $t77'");
        engine.execute("SET t79 = 'EXECUTE IMMEDIATE $t78'");
        engine.execute("SET t80 = 'EXECUTE IMMEDIATE $t79'");
        engine.execute("SET t81 = 'EXECUTE IMMEDIATE $t80'");
        engine.execute("SET t82 = 'EXECUTE IMMEDIATE $t81'");
        engine.execute("SET t83 = 'EXECUTE IMMEDIATE $t82'");
        engine.execute("SET t84 = 'EXECUTE IMMEDIATE $t83'");
        engine.execute("SET t85 = 'EXECUTE IMMEDIATE $t84'");
        engine.execute("SET t86 = 'EXECUTE IMMEDIATE $t85'");
        engine.execute("SET t87 = 'EXECUTE IMMEDIATE $t86'");
        engine.execute("SET t88 = 'EXECUTE IMMEDIATE $t87'");
        engine.execute("SET t89 = 'EXECUTE IMMEDIATE $t88'");
        engine.execute("SET t90 = 'EXECUTE IMMEDIATE $t89'");
        engine.execute("SET t91 = 'EXECUTE IMMEDIATE $t90'");
        engine.execute("SET t92 = 'EXECUTE IMMEDIATE $t91'");
        engine.execute("SET t93 = 'EXECUTE IMMEDIATE $t92'");
        engine.execute("SET t94 = 'EXECUTE IMMEDIATE $t93'");
        engine.execute("SET t95 = 'EXECUTE IMMEDIATE $t94'");
        engine.execute("SET t96 = 'EXECUTE IMMEDIATE $t95'");
        engine.execute("SET t97 = 'EXECUTE IMMEDIATE $t96'");
        engine.execute("SET t98 = 'EXECUTE IMMEDIATE $t97'");
        engine.execute("SET t99 = 'EXECUTE IMMEDIATE $t98'");
        engine.execute("SET t100 = 'EXECUTE IMMEDIATE $t99'");
        engine.execute("SET t101 = 'EXECUTE IMMEDIATE $t100'");
        engine.execute("SET t102 = 'EXECUTE IMMEDIATE $t101'");
        engine.execute("SET t103 = 'EXECUTE IMMEDIATE $t102'");
        engine.execute("SET t104 = 'EXECUTE IMMEDIATE $t103'");
        engine.execute("SET t105 = 'EXECUTE IMMEDIATE $t104'");
        engine.execute("SET t106 = 'EXECUTE IMMEDIATE $t105'");
        engine.execute("SET t107 = 'EXECUTE IMMEDIATE $t106'");
        engine.execute("SET t108 = 'EXECUTE IMMEDIATE $t107'");
        engine.execute("SET t109 = 'EXECUTE IMMEDIATE $t108'");
        engine.execute("SET t110 = 'EXECUTE IMMEDIATE $t109'");
        engine.execute("SET t111 = 'EXECUTE IMMEDIATE $t110'");
        engine.execute("SET t112 = 'EXECUTE IMMEDIATE $t111'");
        engine.execute("SET t113 = 'EXECUTE IMMEDIATE $t112'");
        engine.execute("SET t114 = 'EXECUTE IMMEDIATE $t113'");
        engine.execute("SET t115 = 'EXECUTE IMMEDIATE $t114'");
        engine.execute("SET t116 = 'EXECUTE IMMEDIATE $t115'");
        engine.execute("SET t117 = 'EXECUTE IMMEDIATE $t116'");
        engine.execute("SET t118 = 'EXECUTE IMMEDIATE $t117'");
        engine.execute("SET t119 = 'EXECUTE IMMEDIATE $t118'");
        engine.execute("SET t120 = 'EXECUTE IMMEDIATE $t119'");
        engine.execute("SET t121 = 'EXECUTE IMMEDIATE $t120'");
        engine.execute("SET t122 = 'EXECUTE IMMEDIATE $t121'");
        engine.execute("SET t123 = 'EXECUTE IMMEDIATE $t122'");
        engine.execute("SET t124 = 'EXECUTE IMMEDIATE $t123'");
        engine.execute("SET t125 = 'EXECUTE IMMEDIATE $t124'");
        engine.execute("SET t126 = 'EXECUTE IMMEDIATE $t125'");
        engine.execute("SET t127 = 'EXECUTE IMMEDIATE $t126'");
        engine.execute("SET t128 = 'EXECUTE IMMEDIATE $t127'");
        engine.execute("SET t129 = 'EXECUTE IMMEDIATE $t128'");
        engine.execute("SET t130 = 'EXECUTE IMMEDIATE $t129'");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $t1", "bottom"},
            {"EXECUTE IMMEDIATE $t18", "bottom"},
            {"EXECUTE IMMEDIATE $t19", "bottom"},
            {"EXECUTE IMMEDIATE $t40", "bottom"},
            {"EXECUTE IMMEDIATE $t80", "bottom"},
            {"EXECUTE IMMEDIATE $t99", "bottom"},
            {"EXECUTE IMMEDIATE $t100", "bottom"},
            {"EXECUTE IMMEDIATE $t101", "bottom"},
            {"EXECUTE IMMEDIATE $t130", "bottom"},
        });
    }

    @Test
    public void twoHundredDeep() {
        engine.execute("SET t0 = 'SELECT ''bottom'''");
        engine.execute("SET (t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15, t16, t17, t18, t19, "
            + "t20, t21, t22, t23, t24, t25, t26, t27, t28, t29, t30, t31, t32, t33, t34, t35, t36, t37, "
            + "t38, t39, t40, t41, t42, t43, t44, t45, t46, t47, t48, t49, t50) = ('EXECUTE IMMEDIATE "
            + "$t0', 'EXECUTE IMMEDIATE $t1', 'EXECUTE IMMEDIATE $t2', 'EXECUTE IMMEDIATE $t3', 'EXECUTE "
            + "IMMEDIATE $t4', 'EXECUTE IMMEDIATE $t5', 'EXECUTE IMMEDIATE $t6', 'EXECUTE IMMEDIATE $t7', "
            + "'EXECUTE IMMEDIATE $t8', 'EXECUTE IMMEDIATE $t9', 'EXECUTE IMMEDIATE $t10', 'EXECUTE "
            + "IMMEDIATE $t11', 'EXECUTE IMMEDIATE $t12', 'EXECUTE IMMEDIATE $t13', 'EXECUTE IMMEDIATE "
            + "$t14', 'EXECUTE IMMEDIATE $t15', 'EXECUTE IMMEDIATE $t16', 'EXECUTE IMMEDIATE $t17', "
            + "'EXECUTE IMMEDIATE $t18', 'EXECUTE IMMEDIATE $t19', 'EXECUTE IMMEDIATE $t20', 'EXECUTE "
            + "IMMEDIATE $t21', 'EXECUTE IMMEDIATE $t22', 'EXECUTE IMMEDIATE $t23', 'EXECUTE IMMEDIATE "
            + "$t24', 'EXECUTE IMMEDIATE $t25', 'EXECUTE IMMEDIATE $t26', 'EXECUTE IMMEDIATE $t27', "
            + "'EXECUTE IMMEDIATE $t28', 'EXECUTE IMMEDIATE $t29', 'EXECUTE IMMEDIATE $t30', 'EXECUTE "
            + "IMMEDIATE $t31', 'EXECUTE IMMEDIATE $t32', 'EXECUTE IMMEDIATE $t33', 'EXECUTE IMMEDIATE "
            + "$t34', 'EXECUTE IMMEDIATE $t35', 'EXECUTE IMMEDIATE $t36', 'EXECUTE IMMEDIATE $t37', "
            + "'EXECUTE IMMEDIATE $t38', 'EXECUTE IMMEDIATE $t39', 'EXECUTE IMMEDIATE $t40', 'EXECUTE "
            + "IMMEDIATE $t41', 'EXECUTE IMMEDIATE $t42', 'EXECUTE IMMEDIATE $t43', 'EXECUTE IMMEDIATE "
            + "$t44', 'EXECUTE IMMEDIATE $t45', 'EXECUTE IMMEDIATE $t46', 'EXECUTE IMMEDIATE $t47', "
            + "'EXECUTE IMMEDIATE $t48', 'EXECUTE IMMEDIATE $t49')");
        engine.execute("SET (t51, t52, t53, t54, t55, t56, t57, t58, t59, t60, t61, t62, t63, t64, t65, t66, t67, "
            + "t68, t69, t70, t71, t72, t73, t74, t75, t76, t77, t78, t79, t80, t81, t82, t83, t84, t85, "
            + "t86, t87, t88, t89, t90, t91, t92, t93, t94, t95, t96, t97, t98, t99, t100) = ('EXECUTE "
            + "IMMEDIATE $t50', 'EXECUTE IMMEDIATE $t51', 'EXECUTE IMMEDIATE $t52', 'EXECUTE IMMEDIATE "
            + "$t53', 'EXECUTE IMMEDIATE $t54', 'EXECUTE IMMEDIATE $t55', 'EXECUTE IMMEDIATE $t56', "
            + "'EXECUTE IMMEDIATE $t57', 'EXECUTE IMMEDIATE $t58', 'EXECUTE IMMEDIATE $t59', 'EXECUTE "
            + "IMMEDIATE $t60', 'EXECUTE IMMEDIATE $t61', 'EXECUTE IMMEDIATE $t62', 'EXECUTE IMMEDIATE "
            + "$t63', 'EXECUTE IMMEDIATE $t64', 'EXECUTE IMMEDIATE $t65', 'EXECUTE IMMEDIATE $t66', "
            + "'EXECUTE IMMEDIATE $t67', 'EXECUTE IMMEDIATE $t68', 'EXECUTE IMMEDIATE $t69', 'EXECUTE "
            + "IMMEDIATE $t70', 'EXECUTE IMMEDIATE $t71', 'EXECUTE IMMEDIATE $t72', 'EXECUTE IMMEDIATE "
            + "$t73', 'EXECUTE IMMEDIATE $t74', 'EXECUTE IMMEDIATE $t75', 'EXECUTE IMMEDIATE $t76', "
            + "'EXECUTE IMMEDIATE $t77', 'EXECUTE IMMEDIATE $t78', 'EXECUTE IMMEDIATE $t79', 'EXECUTE "
            + "IMMEDIATE $t80', 'EXECUTE IMMEDIATE $t81', 'EXECUTE IMMEDIATE $t82', 'EXECUTE IMMEDIATE "
            + "$t83', 'EXECUTE IMMEDIATE $t84', 'EXECUTE IMMEDIATE $t85', 'EXECUTE IMMEDIATE $t86', "
            + "'EXECUTE IMMEDIATE $t87', 'EXECUTE IMMEDIATE $t88', 'EXECUTE IMMEDIATE $t89', 'EXECUTE "
            + "IMMEDIATE $t90', 'EXECUTE IMMEDIATE $t91', 'EXECUTE IMMEDIATE $t92', 'EXECUTE IMMEDIATE "
            + "$t93', 'EXECUTE IMMEDIATE $t94', 'EXECUTE IMMEDIATE $t95', 'EXECUTE IMMEDIATE $t96', "
            + "'EXECUTE IMMEDIATE $t97', 'EXECUTE IMMEDIATE $t98', 'EXECUTE IMMEDIATE $t99')");
        engine.execute("SET (t101, t102, t103, t104, t105, t106, t107, t108, t109, t110, t111, t112, t113, t114, "
            + "t115, t116, t117, t118, t119, t120, t121, t122, t123, t124, t125, t126, t127, t128, t129, "
            + "t130, t131, t132, t133, t134, t135, t136, t137, t138, t139, t140, t141, t142, t143, t144, "
            + "t145, t146, t147, t148, t149, t150) = ('EXECUTE IMMEDIATE $t100', 'EXECUTE IMMEDIATE "
            + "$t101', 'EXECUTE IMMEDIATE $t102', 'EXECUTE IMMEDIATE $t103', 'EXECUTE IMMEDIATE $t104', "
            + "'EXECUTE IMMEDIATE $t105', 'EXECUTE IMMEDIATE $t106', 'EXECUTE IMMEDIATE $t107', 'EXECUTE "
            + "IMMEDIATE $t108', 'EXECUTE IMMEDIATE $t109', 'EXECUTE IMMEDIATE $t110', 'EXECUTE IMMEDIATE "
            + "$t111', 'EXECUTE IMMEDIATE $t112', 'EXECUTE IMMEDIATE $t113', 'EXECUTE IMMEDIATE $t114', "
            + "'EXECUTE IMMEDIATE $t115', 'EXECUTE IMMEDIATE $t116', 'EXECUTE IMMEDIATE $t117', 'EXECUTE "
            + "IMMEDIATE $t118', 'EXECUTE IMMEDIATE $t119', 'EXECUTE IMMEDIATE $t120', 'EXECUTE IMMEDIATE "
            + "$t121', 'EXECUTE IMMEDIATE $t122', 'EXECUTE IMMEDIATE $t123', 'EXECUTE IMMEDIATE $t124', "
            + "'EXECUTE IMMEDIATE $t125', 'EXECUTE IMMEDIATE $t126', 'EXECUTE IMMEDIATE $t127', 'EXECUTE "
            + "IMMEDIATE $t128', 'EXECUTE IMMEDIATE $t129', 'EXECUTE IMMEDIATE $t130', 'EXECUTE IMMEDIATE "
            + "$t131', 'EXECUTE IMMEDIATE $t132', 'EXECUTE IMMEDIATE $t133', 'EXECUTE IMMEDIATE $t134', "
            + "'EXECUTE IMMEDIATE $t135', 'EXECUTE IMMEDIATE $t136', 'EXECUTE IMMEDIATE $t137', 'EXECUTE "
            + "IMMEDIATE $t138', 'EXECUTE IMMEDIATE $t139', 'EXECUTE IMMEDIATE $t140', 'EXECUTE IMMEDIATE "
            + "$t141', 'EXECUTE IMMEDIATE $t142', 'EXECUTE IMMEDIATE $t143', 'EXECUTE IMMEDIATE $t144', "
            + "'EXECUTE IMMEDIATE $t145', 'EXECUTE IMMEDIATE $t146', 'EXECUTE IMMEDIATE $t147', 'EXECUTE "
            + "IMMEDIATE $t148', 'EXECUTE IMMEDIATE $t149')");
        engine.execute("SET (t151, t152, t153, t154, t155, t156, t157, t158, t159, t160, t161, t162, t163, t164, "
            + "t165, t166, t167, t168, t169, t170, t171, t172, t173, t174, t175, t176, t177, t178, t179, "
            + "t180, t181, t182, t183, t184, t185, t186, t187, t188, t189, t190, t191, t192, t193, t194, "
            + "t195, t196, t197, t198, t199, t200) = ('EXECUTE IMMEDIATE $t150', 'EXECUTE IMMEDIATE "
            + "$t151', 'EXECUTE IMMEDIATE $t152', 'EXECUTE IMMEDIATE $t153', 'EXECUTE IMMEDIATE $t154', "
            + "'EXECUTE IMMEDIATE $t155', 'EXECUTE IMMEDIATE $t156', 'EXECUTE IMMEDIATE $t157', 'EXECUTE "
            + "IMMEDIATE $t158', 'EXECUTE IMMEDIATE $t159', 'EXECUTE IMMEDIATE $t160', 'EXECUTE IMMEDIATE "
            + "$t161', 'EXECUTE IMMEDIATE $t162', 'EXECUTE IMMEDIATE $t163', 'EXECUTE IMMEDIATE $t164', "
            + "'EXECUTE IMMEDIATE $t165', 'EXECUTE IMMEDIATE $t166', 'EXECUTE IMMEDIATE $t167', 'EXECUTE "
            + "IMMEDIATE $t168', 'EXECUTE IMMEDIATE $t169', 'EXECUTE IMMEDIATE $t170', 'EXECUTE IMMEDIATE "
            + "$t171', 'EXECUTE IMMEDIATE $t172', 'EXECUTE IMMEDIATE $t173', 'EXECUTE IMMEDIATE $t174', "
            + "'EXECUTE IMMEDIATE $t175', 'EXECUTE IMMEDIATE $t176', 'EXECUTE IMMEDIATE $t177', 'EXECUTE "
            + "IMMEDIATE $t178', 'EXECUTE IMMEDIATE $t179', 'EXECUTE IMMEDIATE $t180', 'EXECUTE IMMEDIATE "
            + "$t181', 'EXECUTE IMMEDIATE $t182', 'EXECUTE IMMEDIATE $t183', 'EXECUTE IMMEDIATE $t184', "
            + "'EXECUTE IMMEDIATE $t185', 'EXECUTE IMMEDIATE $t186', 'EXECUTE IMMEDIATE $t187', 'EXECUTE "
            + "IMMEDIATE $t188', 'EXECUTE IMMEDIATE $t189', 'EXECUTE IMMEDIATE $t190', 'EXECUTE IMMEDIATE "
            + "$t191', 'EXECUTE IMMEDIATE $t192', 'EXECUTE IMMEDIATE $t193', 'EXECUTE IMMEDIATE $t194', "
            + "'EXECUTE IMMEDIATE $t195', 'EXECUTE IMMEDIATE $t196', 'EXECUTE IMMEDIATE $t197', 'EXECUTE "
            + "IMMEDIATE $t198', 'EXECUTE IMMEDIATE $t199')");
        engine.execute("SET (t201, t202, t203, t204, t205, t206, t207, t208, t209, t210, t211, t212, t213, t214, "
            + "t215, t216, t217, t218, t219, t220, t221, t222, t223, t224, t225, t226, t227, t228, t229, "
            + "t230, t231, t232, t233, t234, t235, t236, t237, t238, t239, t240, t241, t242, t243, t244, "
            + "t245, t246, t247, t248, t249, t250) = ('EXECUTE IMMEDIATE $t200', 'EXECUTE IMMEDIATE "
            + "$t201', 'EXECUTE IMMEDIATE $t202', 'EXECUTE IMMEDIATE $t203', 'EXECUTE IMMEDIATE $t204', "
            + "'EXECUTE IMMEDIATE $t205', 'EXECUTE IMMEDIATE $t206', 'EXECUTE IMMEDIATE $t207', 'EXECUTE "
            + "IMMEDIATE $t208', 'EXECUTE IMMEDIATE $t209', 'EXECUTE IMMEDIATE $t210', 'EXECUTE IMMEDIATE "
            + "$t211', 'EXECUTE IMMEDIATE $t212', 'EXECUTE IMMEDIATE $t213', 'EXECUTE IMMEDIATE $t214', "
            + "'EXECUTE IMMEDIATE $t215', 'EXECUTE IMMEDIATE $t216', 'EXECUTE IMMEDIATE $t217', 'EXECUTE "
            + "IMMEDIATE $t218', 'EXECUTE IMMEDIATE $t219', 'EXECUTE IMMEDIATE $t220', 'EXECUTE IMMEDIATE "
            + "$t221', 'EXECUTE IMMEDIATE $t222', 'EXECUTE IMMEDIATE $t223', 'EXECUTE IMMEDIATE $t224', "
            + "'EXECUTE IMMEDIATE $t225', 'EXECUTE IMMEDIATE $t226', 'EXECUTE IMMEDIATE $t227', 'EXECUTE "
            + "IMMEDIATE $t228', 'EXECUTE IMMEDIATE $t229', 'EXECUTE IMMEDIATE $t230', 'EXECUTE IMMEDIATE "
            + "$t231', 'EXECUTE IMMEDIATE $t232', 'EXECUTE IMMEDIATE $t233', 'EXECUTE IMMEDIATE $t234', "
            + "'EXECUTE IMMEDIATE $t235', 'EXECUTE IMMEDIATE $t236', 'EXECUTE IMMEDIATE $t237', 'EXECUTE "
            + "IMMEDIATE $t238', 'EXECUTE IMMEDIATE $t239', 'EXECUTE IMMEDIATE $t240', 'EXECUTE IMMEDIATE "
            + "$t241', 'EXECUTE IMMEDIATE $t242', 'EXECUTE IMMEDIATE $t243', 'EXECUTE IMMEDIATE $t244', "
            + "'EXECUTE IMMEDIATE $t245', 'EXECUTE IMMEDIATE $t246', 'EXECUTE IMMEDIATE $t247', 'EXECUTE "
            + "IMMEDIATE $t248', 'EXECUTE IMMEDIATE $t249')");
        engine.execute("SET (t251, t252, t253, t254, t255, t256, t257, t258, t259, t260, t261, t262, t263, t264, "
            + "t265, t266, t267, t268, t269, t270, t271, t272, t273, t274, t275, t276, t277, t278, t279, "
            + "t280, t281, t282, t283, t284, t285, t286, t287, t288, t289, t290, t291, t292, t293, t294, "
            + "t295, t296, t297, t298, t299, t300) = ('EXECUTE IMMEDIATE $t250', 'EXECUTE IMMEDIATE "
            + "$t251', 'EXECUTE IMMEDIATE $t252', 'EXECUTE IMMEDIATE $t253', 'EXECUTE IMMEDIATE $t254', "
            + "'EXECUTE IMMEDIATE $t255', 'EXECUTE IMMEDIATE $t256', 'EXECUTE IMMEDIATE $t257', 'EXECUTE "
            + "IMMEDIATE $t258', 'EXECUTE IMMEDIATE $t259', 'EXECUTE IMMEDIATE $t260', 'EXECUTE IMMEDIATE "
            + "$t261', 'EXECUTE IMMEDIATE $t262', 'EXECUTE IMMEDIATE $t263', 'EXECUTE IMMEDIATE $t264', "
            + "'EXECUTE IMMEDIATE $t265', 'EXECUTE IMMEDIATE $t266', 'EXECUTE IMMEDIATE $t267', 'EXECUTE "
            + "IMMEDIATE $t268', 'EXECUTE IMMEDIATE $t269', 'EXECUTE IMMEDIATE $t270', 'EXECUTE IMMEDIATE "
            + "$t271', 'EXECUTE IMMEDIATE $t272', 'EXECUTE IMMEDIATE $t273', 'EXECUTE IMMEDIATE $t274', "
            + "'EXECUTE IMMEDIATE $t275', 'EXECUTE IMMEDIATE $t276', 'EXECUTE IMMEDIATE $t277', 'EXECUTE "
            + "IMMEDIATE $t278', 'EXECUTE IMMEDIATE $t279', 'EXECUTE IMMEDIATE $t280', 'EXECUTE IMMEDIATE "
            + "$t281', 'EXECUTE IMMEDIATE $t282', 'EXECUTE IMMEDIATE $t283', 'EXECUTE IMMEDIATE $t284', "
            + "'EXECUTE IMMEDIATE $t285', 'EXECUTE IMMEDIATE $t286', 'EXECUTE IMMEDIATE $t287', 'EXECUTE "
            + "IMMEDIATE $t288', 'EXECUTE IMMEDIATE $t289', 'EXECUTE IMMEDIATE $t290', 'EXECUTE IMMEDIATE "
            + "$t291', 'EXECUTE IMMEDIATE $t292', 'EXECUTE IMMEDIATE $t293', 'EXECUTE IMMEDIATE $t294', "
            + "'EXECUTE IMMEDIATE $t295', 'EXECUTE IMMEDIATE $t296', 'EXECUTE IMMEDIATE $t297', 'EXECUTE "
            + "IMMEDIATE $t298', 'EXECUTE IMMEDIATE $t299')");
        engine.execute("SET (t301, t302, t303, t304, t305, t306, t307, t308, t309, t310, t311, t312, t313, t314, "
            + "t315, t316, t317, t318, t319, t320, t321, t322, t323, t324, t325, t326, t327, t328, t329, "
            + "t330, t331, t332, t333, t334, t335, t336, t337, t338, t339, t340, t341, t342, t343, t344, "
            + "t345, t346, t347, t348, t349, t350) = ('EXECUTE IMMEDIATE $t300', 'EXECUTE IMMEDIATE "
            + "$t301', 'EXECUTE IMMEDIATE $t302', 'EXECUTE IMMEDIATE $t303', 'EXECUTE IMMEDIATE $t304', "
            + "'EXECUTE IMMEDIATE $t305', 'EXECUTE IMMEDIATE $t306', 'EXECUTE IMMEDIATE $t307', 'EXECUTE "
            + "IMMEDIATE $t308', 'EXECUTE IMMEDIATE $t309', 'EXECUTE IMMEDIATE $t310', 'EXECUTE IMMEDIATE "
            + "$t311', 'EXECUTE IMMEDIATE $t312', 'EXECUTE IMMEDIATE $t313', 'EXECUTE IMMEDIATE $t314', "
            + "'EXECUTE IMMEDIATE $t315', 'EXECUTE IMMEDIATE $t316', 'EXECUTE IMMEDIATE $t317', 'EXECUTE "
            + "IMMEDIATE $t318', 'EXECUTE IMMEDIATE $t319', 'EXECUTE IMMEDIATE $t320', 'EXECUTE IMMEDIATE "
            + "$t321', 'EXECUTE IMMEDIATE $t322', 'EXECUTE IMMEDIATE $t323', 'EXECUTE IMMEDIATE $t324', "
            + "'EXECUTE IMMEDIATE $t325', 'EXECUTE IMMEDIATE $t326', 'EXECUTE IMMEDIATE $t327', 'EXECUTE "
            + "IMMEDIATE $t328', 'EXECUTE IMMEDIATE $t329', 'EXECUTE IMMEDIATE $t330', 'EXECUTE IMMEDIATE "
            + "$t331', 'EXECUTE IMMEDIATE $t332', 'EXECUTE IMMEDIATE $t333', 'EXECUTE IMMEDIATE $t334', "
            + "'EXECUTE IMMEDIATE $t335', 'EXECUTE IMMEDIATE $t336', 'EXECUTE IMMEDIATE $t337', 'EXECUTE "
            + "IMMEDIATE $t338', 'EXECUTE IMMEDIATE $t339', 'EXECUTE IMMEDIATE $t340', 'EXECUTE IMMEDIATE "
            + "$t341', 'EXECUTE IMMEDIATE $t342', 'EXECUTE IMMEDIATE $t343', 'EXECUTE IMMEDIATE $t344', "
            + "'EXECUTE IMMEDIATE $t345', 'EXECUTE IMMEDIATE $t346', 'EXECUTE IMMEDIATE $t347', 'EXECUTE "
            + "IMMEDIATE $t348', 'EXECUTE IMMEDIATE $t349')");
        engine.execute("SET (t351, t352, t353, t354, t355, t356, t357, t358, t359, t360, t361, t362, t363, t364, "
            + "t365, t366, t367, t368, t369, t370, t371, t372, t373, t374, t375, t376, t377, t378, t379, "
            + "t380, t381, t382, t383, t384, t385, t386, t387, t388, t389, t390, t391, t392, t393, t394, "
            + "t395, t396, t397, t398, t399, t400) = ('EXECUTE IMMEDIATE $t350', 'EXECUTE IMMEDIATE "
            + "$t351', 'EXECUTE IMMEDIATE $t352', 'EXECUTE IMMEDIATE $t353', 'EXECUTE IMMEDIATE $t354', "
            + "'EXECUTE IMMEDIATE $t355', 'EXECUTE IMMEDIATE $t356', 'EXECUTE IMMEDIATE $t357', 'EXECUTE "
            + "IMMEDIATE $t358', 'EXECUTE IMMEDIATE $t359', 'EXECUTE IMMEDIATE $t360', 'EXECUTE IMMEDIATE "
            + "$t361', 'EXECUTE IMMEDIATE $t362', 'EXECUTE IMMEDIATE $t363', 'EXECUTE IMMEDIATE $t364', "
            + "'EXECUTE IMMEDIATE $t365', 'EXECUTE IMMEDIATE $t366', 'EXECUTE IMMEDIATE $t367', 'EXECUTE "
            + "IMMEDIATE $t368', 'EXECUTE IMMEDIATE $t369', 'EXECUTE IMMEDIATE $t370', 'EXECUTE IMMEDIATE "
            + "$t371', 'EXECUTE IMMEDIATE $t372', 'EXECUTE IMMEDIATE $t373', 'EXECUTE IMMEDIATE $t374', "
            + "'EXECUTE IMMEDIATE $t375', 'EXECUTE IMMEDIATE $t376', 'EXECUTE IMMEDIATE $t377', 'EXECUTE "
            + "IMMEDIATE $t378', 'EXECUTE IMMEDIATE $t379', 'EXECUTE IMMEDIATE $t380', 'EXECUTE IMMEDIATE "
            + "$t381', 'EXECUTE IMMEDIATE $t382', 'EXECUTE IMMEDIATE $t383', 'EXECUTE IMMEDIATE $t384', "
            + "'EXECUTE IMMEDIATE $t385', 'EXECUTE IMMEDIATE $t386', 'EXECUTE IMMEDIATE $t387', 'EXECUTE "
            + "IMMEDIATE $t388', 'EXECUTE IMMEDIATE $t389', 'EXECUTE IMMEDIATE $t390', 'EXECUTE IMMEDIATE "
            + "$t391', 'EXECUTE IMMEDIATE $t392', 'EXECUTE IMMEDIATE $t393', 'EXECUTE IMMEDIATE $t394', "
            + "'EXECUTE IMMEDIATE $t395', 'EXECUTE IMMEDIATE $t396', 'EXECUTE IMMEDIATE $t397', 'EXECUTE "
            + "IMMEDIATE $t398', 'EXECUTE IMMEDIATE $t399')");
        engine.execute("SET (t401, t402, t403, t404, t405, t406, t407, t408, t409, t410, t411, t412, t413, t414, "
            + "t415, t416, t417, t418, t419, t420, t421, t422, t423, t424, t425, t426, t427, t428, t429, "
            + "t430, t431, t432, t433, t434, t435, t436, t437, t438, t439, t440, t441, t442, t443, t444, "
            + "t445, t446, t447, t448, t449, t450) = ('EXECUTE IMMEDIATE $t400', 'EXECUTE IMMEDIATE "
            + "$t401', 'EXECUTE IMMEDIATE $t402', 'EXECUTE IMMEDIATE $t403', 'EXECUTE IMMEDIATE $t404', "
            + "'EXECUTE IMMEDIATE $t405', 'EXECUTE IMMEDIATE $t406', 'EXECUTE IMMEDIATE $t407', 'EXECUTE "
            + "IMMEDIATE $t408', 'EXECUTE IMMEDIATE $t409', 'EXECUTE IMMEDIATE $t410', 'EXECUTE IMMEDIATE "
            + "$t411', 'EXECUTE IMMEDIATE $t412', 'EXECUTE IMMEDIATE $t413', 'EXECUTE IMMEDIATE $t414', "
            + "'EXECUTE IMMEDIATE $t415', 'EXECUTE IMMEDIATE $t416', 'EXECUTE IMMEDIATE $t417', 'EXECUTE "
            + "IMMEDIATE $t418', 'EXECUTE IMMEDIATE $t419', 'EXECUTE IMMEDIATE $t420', 'EXECUTE IMMEDIATE "
            + "$t421', 'EXECUTE IMMEDIATE $t422', 'EXECUTE IMMEDIATE $t423', 'EXECUTE IMMEDIATE $t424', "
            + "'EXECUTE IMMEDIATE $t425', 'EXECUTE IMMEDIATE $t426', 'EXECUTE IMMEDIATE $t427', 'EXECUTE "
            + "IMMEDIATE $t428', 'EXECUTE IMMEDIATE $t429', 'EXECUTE IMMEDIATE $t430', 'EXECUTE IMMEDIATE "
            + "$t431', 'EXECUTE IMMEDIATE $t432', 'EXECUTE IMMEDIATE $t433', 'EXECUTE IMMEDIATE $t434', "
            + "'EXECUTE IMMEDIATE $t435', 'EXECUTE IMMEDIATE $t436', 'EXECUTE IMMEDIATE $t437', 'EXECUTE "
            + "IMMEDIATE $t438', 'EXECUTE IMMEDIATE $t439', 'EXECUTE IMMEDIATE $t440', 'EXECUTE IMMEDIATE "
            + "$t441', 'EXECUTE IMMEDIATE $t442', 'EXECUTE IMMEDIATE $t443', 'EXECUTE IMMEDIATE $t444', "
            + "'EXECUTE IMMEDIATE $t445', 'EXECUTE IMMEDIATE $t446', 'EXECUTE IMMEDIATE $t447', 'EXECUTE "
            + "IMMEDIATE $t448', 'EXECUTE IMMEDIATE $t449')");
        engine.execute("SET (t451, t452, t453, t454, t455, t456, t457, t458, t459, t460, t461, t462, t463, t464, "
            + "t465, t466, t467, t468, t469, t470, t471, t472, t473, t474, t475, t476, t477, t478, t479, "
            + "t480, t481, t482, t483, t484, t485, t486, t487, t488, t489, t490, t491, t492, t493, t494, "
            + "t495, t496, t497, t498, t499, t500) = ('EXECUTE IMMEDIATE $t450', 'EXECUTE IMMEDIATE "
            + "$t451', 'EXECUTE IMMEDIATE $t452', 'EXECUTE IMMEDIATE $t453', 'EXECUTE IMMEDIATE $t454', "
            + "'EXECUTE IMMEDIATE $t455', 'EXECUTE IMMEDIATE $t456', 'EXECUTE IMMEDIATE $t457', 'EXECUTE "
            + "IMMEDIATE $t458', 'EXECUTE IMMEDIATE $t459', 'EXECUTE IMMEDIATE $t460', 'EXECUTE IMMEDIATE "
            + "$t461', 'EXECUTE IMMEDIATE $t462', 'EXECUTE IMMEDIATE $t463', 'EXECUTE IMMEDIATE $t464', "
            + "'EXECUTE IMMEDIATE $t465', 'EXECUTE IMMEDIATE $t466', 'EXECUTE IMMEDIATE $t467', 'EXECUTE "
            + "IMMEDIATE $t468', 'EXECUTE IMMEDIATE $t469', 'EXECUTE IMMEDIATE $t470', 'EXECUTE IMMEDIATE "
            + "$t471', 'EXECUTE IMMEDIATE $t472', 'EXECUTE IMMEDIATE $t473', 'EXECUTE IMMEDIATE $t474', "
            + "'EXECUTE IMMEDIATE $t475', 'EXECUTE IMMEDIATE $t476', 'EXECUTE IMMEDIATE $t477', 'EXECUTE "
            + "IMMEDIATE $t478', 'EXECUTE IMMEDIATE $t479', 'EXECUTE IMMEDIATE $t480', 'EXECUTE IMMEDIATE "
            + "$t481', 'EXECUTE IMMEDIATE $t482', 'EXECUTE IMMEDIATE $t483', 'EXECUTE IMMEDIATE $t484', "
            + "'EXECUTE IMMEDIATE $t485', 'EXECUTE IMMEDIATE $t486', 'EXECUTE IMMEDIATE $t487', 'EXECUTE "
            + "IMMEDIATE $t488', 'EXECUTE IMMEDIATE $t489', 'EXECUTE IMMEDIATE $t490', 'EXECUTE IMMEDIATE "
            + "$t491', 'EXECUTE IMMEDIATE $t492', 'EXECUTE IMMEDIATE $t493', 'EXECUTE IMMEDIATE $t494', "
            + "'EXECUTE IMMEDIATE $t495', 'EXECUTE IMMEDIATE $t496', 'EXECUTE IMMEDIATE $t497', 'EXECUTE "
            + "IMMEDIATE $t498', 'EXECUTE IMMEDIATE $t499')");
        engine.execute("SET (t501, t502, t503, t504, t505, t506, t507, t508, t509, t510, t511, t512, t513, t514, "
            + "t515, t516, t517, t518, t519, t520, t521, t522, t523, t524, t525, t526, t527, t528, t529, "
            + "t530, t531, t532, t533, t534, t535, t536, t537, t538, t539, t540, t541, t542, t543, t544, "
            + "t545, t546, t547, t548, t549, t550) = ('EXECUTE IMMEDIATE $t500', 'EXECUTE IMMEDIATE "
            + "$t501', 'EXECUTE IMMEDIATE $t502', 'EXECUTE IMMEDIATE $t503', 'EXECUTE IMMEDIATE $t504', "
            + "'EXECUTE IMMEDIATE $t505', 'EXECUTE IMMEDIATE $t506', 'EXECUTE IMMEDIATE $t507', 'EXECUTE "
            + "IMMEDIATE $t508', 'EXECUTE IMMEDIATE $t509', 'EXECUTE IMMEDIATE $t510', 'EXECUTE IMMEDIATE "
            + "$t511', 'EXECUTE IMMEDIATE $t512', 'EXECUTE IMMEDIATE $t513', 'EXECUTE IMMEDIATE $t514', "
            + "'EXECUTE IMMEDIATE $t515', 'EXECUTE IMMEDIATE $t516', 'EXECUTE IMMEDIATE $t517', 'EXECUTE "
            + "IMMEDIATE $t518', 'EXECUTE IMMEDIATE $t519', 'EXECUTE IMMEDIATE $t520', 'EXECUTE IMMEDIATE "
            + "$t521', 'EXECUTE IMMEDIATE $t522', 'EXECUTE IMMEDIATE $t523', 'EXECUTE IMMEDIATE $t524', "
            + "'EXECUTE IMMEDIATE $t525', 'EXECUTE IMMEDIATE $t526', 'EXECUTE IMMEDIATE $t527', 'EXECUTE "
            + "IMMEDIATE $t528', 'EXECUTE IMMEDIATE $t529', 'EXECUTE IMMEDIATE $t530', 'EXECUTE IMMEDIATE "
            + "$t531', 'EXECUTE IMMEDIATE $t532', 'EXECUTE IMMEDIATE $t533', 'EXECUTE IMMEDIATE $t534', "
            + "'EXECUTE IMMEDIATE $t535', 'EXECUTE IMMEDIATE $t536', 'EXECUTE IMMEDIATE $t537', 'EXECUTE "
            + "IMMEDIATE $t538', 'EXECUTE IMMEDIATE $t539', 'EXECUTE IMMEDIATE $t540', 'EXECUTE IMMEDIATE "
            + "$t541', 'EXECUTE IMMEDIATE $t542', 'EXECUTE IMMEDIATE $t543', 'EXECUTE IMMEDIATE $t544', "
            + "'EXECUTE IMMEDIATE $t545', 'EXECUTE IMMEDIATE $t546', 'EXECUTE IMMEDIATE $t547', 'EXECUTE "
            + "IMMEDIATE $t548', 'EXECUTE IMMEDIATE $t549')");
        engine.execute("SET (t551, t552, t553, t554, t555, t556, t557, t558, t559, t560, t561, t562, t563, t564, "
            + "t565, t566, t567, t568, t569, t570, t571, t572, t573, t574, t575, t576, t577, t578, t579, "
            + "t580, t581, t582, t583, t584, t585, t586, t587, t588, t589, t590, t591, t592, t593, t594, "
            + "t595, t596, t597, t598, t599, t600) = ('EXECUTE IMMEDIATE $t550', 'EXECUTE IMMEDIATE "
            + "$t551', 'EXECUTE IMMEDIATE $t552', 'EXECUTE IMMEDIATE $t553', 'EXECUTE IMMEDIATE $t554', "
            + "'EXECUTE IMMEDIATE $t555', 'EXECUTE IMMEDIATE $t556', 'EXECUTE IMMEDIATE $t557', 'EXECUTE "
            + "IMMEDIATE $t558', 'EXECUTE IMMEDIATE $t559', 'EXECUTE IMMEDIATE $t560', 'EXECUTE IMMEDIATE "
            + "$t561', 'EXECUTE IMMEDIATE $t562', 'EXECUTE IMMEDIATE $t563', 'EXECUTE IMMEDIATE $t564', "
            + "'EXECUTE IMMEDIATE $t565', 'EXECUTE IMMEDIATE $t566', 'EXECUTE IMMEDIATE $t567', 'EXECUTE "
            + "IMMEDIATE $t568', 'EXECUTE IMMEDIATE $t569', 'EXECUTE IMMEDIATE $t570', 'EXECUTE IMMEDIATE "
            + "$t571', 'EXECUTE IMMEDIATE $t572', 'EXECUTE IMMEDIATE $t573', 'EXECUTE IMMEDIATE $t574', "
            + "'EXECUTE IMMEDIATE $t575', 'EXECUTE IMMEDIATE $t576', 'EXECUTE IMMEDIATE $t577', 'EXECUTE "
            + "IMMEDIATE $t578', 'EXECUTE IMMEDIATE $t579', 'EXECUTE IMMEDIATE $t580', 'EXECUTE IMMEDIATE "
            + "$t581', 'EXECUTE IMMEDIATE $t582', 'EXECUTE IMMEDIATE $t583', 'EXECUTE IMMEDIATE $t584', "
            + "'EXECUTE IMMEDIATE $t585', 'EXECUTE IMMEDIATE $t586', 'EXECUTE IMMEDIATE $t587', 'EXECUTE "
            + "IMMEDIATE $t588', 'EXECUTE IMMEDIATE $t589', 'EXECUTE IMMEDIATE $t590', 'EXECUTE IMMEDIATE "
            + "$t591', 'EXECUTE IMMEDIATE $t592', 'EXECUTE IMMEDIATE $t593', 'EXECUTE IMMEDIATE $t594', "
            + "'EXECUTE IMMEDIATE $t595', 'EXECUTE IMMEDIATE $t596', 'EXECUTE IMMEDIATE $t597', 'EXECUTE "
            + "IMMEDIATE $t598', 'EXECUTE IMMEDIATE $t599')");
        engine.execute("SET (t601, t602, t603, t604, t605, t606, t607, t608, t609, t610, t611, t612, t613, t614, "
            + "t615, t616, t617, t618, t619, t620, t621, t622, t623, t624, t625, t626, t627, t628, t629, "
            + "t630, t631, t632, t633, t634, t635, t636, t637, t638, t639, t640, t641, t642, t643, t644, "
            + "t645, t646, t647, t648, t649, t650) = ('EXECUTE IMMEDIATE $t600', 'EXECUTE IMMEDIATE "
            + "$t601', 'EXECUTE IMMEDIATE $t602', 'EXECUTE IMMEDIATE $t603', 'EXECUTE IMMEDIATE $t604', "
            + "'EXECUTE IMMEDIATE $t605', 'EXECUTE IMMEDIATE $t606', 'EXECUTE IMMEDIATE $t607', 'EXECUTE "
            + "IMMEDIATE $t608', 'EXECUTE IMMEDIATE $t609', 'EXECUTE IMMEDIATE $t610', 'EXECUTE IMMEDIATE "
            + "$t611', 'EXECUTE IMMEDIATE $t612', 'EXECUTE IMMEDIATE $t613', 'EXECUTE IMMEDIATE $t614', "
            + "'EXECUTE IMMEDIATE $t615', 'EXECUTE IMMEDIATE $t616', 'EXECUTE IMMEDIATE $t617', 'EXECUTE "
            + "IMMEDIATE $t618', 'EXECUTE IMMEDIATE $t619', 'EXECUTE IMMEDIATE $t620', 'EXECUTE IMMEDIATE "
            + "$t621', 'EXECUTE IMMEDIATE $t622', 'EXECUTE IMMEDIATE $t623', 'EXECUTE IMMEDIATE $t624', "
            + "'EXECUTE IMMEDIATE $t625', 'EXECUTE IMMEDIATE $t626', 'EXECUTE IMMEDIATE $t627', 'EXECUTE "
            + "IMMEDIATE $t628', 'EXECUTE IMMEDIATE $t629', 'EXECUTE IMMEDIATE $t630', 'EXECUTE IMMEDIATE "
            + "$t631', 'EXECUTE IMMEDIATE $t632', 'EXECUTE IMMEDIATE $t633', 'EXECUTE IMMEDIATE $t634', "
            + "'EXECUTE IMMEDIATE $t635', 'EXECUTE IMMEDIATE $t636', 'EXECUTE IMMEDIATE $t637', 'EXECUTE "
            + "IMMEDIATE $t638', 'EXECUTE IMMEDIATE $t639', 'EXECUTE IMMEDIATE $t640', 'EXECUTE IMMEDIATE "
            + "$t641', 'EXECUTE IMMEDIATE $t642', 'EXECUTE IMMEDIATE $t643', 'EXECUTE IMMEDIATE $t644', "
            + "'EXECUTE IMMEDIATE $t645', 'EXECUTE IMMEDIATE $t646', 'EXECUTE IMMEDIATE $t647', 'EXECUTE "
            + "IMMEDIATE $t648', 'EXECUTE IMMEDIATE $t649')");
        engine.execute("SET (t651, t652, t653, t654, t655, t656, t657, t658, t659, t660, t661, t662, t663, t664, "
            + "t665, t666, t667, t668, t669, t670, t671, t672, t673, t674, t675, t676, t677, t678, t679, "
            + "t680, t681, t682, t683, t684, t685, t686, t687, t688, t689, t690, t691, t692, t693, t694, "
            + "t695, t696, t697, t698, t699, t700) = ('EXECUTE IMMEDIATE $t650', 'EXECUTE IMMEDIATE "
            + "$t651', 'EXECUTE IMMEDIATE $t652', 'EXECUTE IMMEDIATE $t653', 'EXECUTE IMMEDIATE $t654', "
            + "'EXECUTE IMMEDIATE $t655', 'EXECUTE IMMEDIATE $t656', 'EXECUTE IMMEDIATE $t657', 'EXECUTE "
            + "IMMEDIATE $t658', 'EXECUTE IMMEDIATE $t659', 'EXECUTE IMMEDIATE $t660', 'EXECUTE IMMEDIATE "
            + "$t661', 'EXECUTE IMMEDIATE $t662', 'EXECUTE IMMEDIATE $t663', 'EXECUTE IMMEDIATE $t664', "
            + "'EXECUTE IMMEDIATE $t665', 'EXECUTE IMMEDIATE $t666', 'EXECUTE IMMEDIATE $t667', 'EXECUTE "
            + "IMMEDIATE $t668', 'EXECUTE IMMEDIATE $t669', 'EXECUTE IMMEDIATE $t670', 'EXECUTE IMMEDIATE "
            + "$t671', 'EXECUTE IMMEDIATE $t672', 'EXECUTE IMMEDIATE $t673', 'EXECUTE IMMEDIATE $t674', "
            + "'EXECUTE IMMEDIATE $t675', 'EXECUTE IMMEDIATE $t676', 'EXECUTE IMMEDIATE $t677', 'EXECUTE "
            + "IMMEDIATE $t678', 'EXECUTE IMMEDIATE $t679', 'EXECUTE IMMEDIATE $t680', 'EXECUTE IMMEDIATE "
            + "$t681', 'EXECUTE IMMEDIATE $t682', 'EXECUTE IMMEDIATE $t683', 'EXECUTE IMMEDIATE $t684', "
            + "'EXECUTE IMMEDIATE $t685', 'EXECUTE IMMEDIATE $t686', 'EXECUTE IMMEDIATE $t687', 'EXECUTE "
            + "IMMEDIATE $t688', 'EXECUTE IMMEDIATE $t689', 'EXECUTE IMMEDIATE $t690', 'EXECUTE IMMEDIATE "
            + "$t691', 'EXECUTE IMMEDIATE $t692', 'EXECUTE IMMEDIATE $t693', 'EXECUTE IMMEDIATE $t694', "
            + "'EXECUTE IMMEDIATE $t695', 'EXECUTE IMMEDIATE $t696', 'EXECUTE IMMEDIATE $t697', 'EXECUTE "
            + "IMMEDIATE $t698', 'EXECUTE IMMEDIATE $t699')");
        engine.execute("SET (t701, t702, t703, t704, t705, t706, t707, t708, t709, t710, t711, t712, t713, t714, "
            + "t715, t716, t717, t718, t719, t720, t721, t722, t723, t724, t725, t726, t727, t728, t729, "
            + "t730, t731, t732, t733, t734, t735, t736, t737, t738, t739, t740, t741, t742, t743, t744, "
            + "t745, t746, t747, t748, t749, t750) = ('EXECUTE IMMEDIATE $t700', 'EXECUTE IMMEDIATE "
            + "$t701', 'EXECUTE IMMEDIATE $t702', 'EXECUTE IMMEDIATE $t703', 'EXECUTE IMMEDIATE $t704', "
            + "'EXECUTE IMMEDIATE $t705', 'EXECUTE IMMEDIATE $t706', 'EXECUTE IMMEDIATE $t707', 'EXECUTE "
            + "IMMEDIATE $t708', 'EXECUTE IMMEDIATE $t709', 'EXECUTE IMMEDIATE $t710', 'EXECUTE IMMEDIATE "
            + "$t711', 'EXECUTE IMMEDIATE $t712', 'EXECUTE IMMEDIATE $t713', 'EXECUTE IMMEDIATE $t714', "
            + "'EXECUTE IMMEDIATE $t715', 'EXECUTE IMMEDIATE $t716', 'EXECUTE IMMEDIATE $t717', 'EXECUTE "
            + "IMMEDIATE $t718', 'EXECUTE IMMEDIATE $t719', 'EXECUTE IMMEDIATE $t720', 'EXECUTE IMMEDIATE "
            + "$t721', 'EXECUTE IMMEDIATE $t722', 'EXECUTE IMMEDIATE $t723', 'EXECUTE IMMEDIATE $t724', "
            + "'EXECUTE IMMEDIATE $t725', 'EXECUTE IMMEDIATE $t726', 'EXECUTE IMMEDIATE $t727', 'EXECUTE "
            + "IMMEDIATE $t728', 'EXECUTE IMMEDIATE $t729', 'EXECUTE IMMEDIATE $t730', 'EXECUTE IMMEDIATE "
            + "$t731', 'EXECUTE IMMEDIATE $t732', 'EXECUTE IMMEDIATE $t733', 'EXECUTE IMMEDIATE $t734', "
            + "'EXECUTE IMMEDIATE $t735', 'EXECUTE IMMEDIATE $t736', 'EXECUTE IMMEDIATE $t737', 'EXECUTE "
            + "IMMEDIATE $t738', 'EXECUTE IMMEDIATE $t739', 'EXECUTE IMMEDIATE $t740', 'EXECUTE IMMEDIATE "
            + "$t741', 'EXECUTE IMMEDIATE $t742', 'EXECUTE IMMEDIATE $t743', 'EXECUTE IMMEDIATE $t744', "
            + "'EXECUTE IMMEDIATE $t745', 'EXECUTE IMMEDIATE $t746', 'EXECUTE IMMEDIATE $t747', 'EXECUTE "
            + "IMMEDIATE $t748', 'EXECUTE IMMEDIATE $t749')");
        engine.execute("SET (t751, t752, t753, t754, t755, t756, t757, t758, t759, t760, t761, t762, t763, t764, "
            + "t765, t766, t767, t768, t769, t770, t771, t772, t773, t774, t775, t776, t777, t778, t779, "
            + "t780, t781, t782, t783, t784, t785, t786, t787, t788, t789, t790, t791, t792, t793, t794, "
            + "t795, t796, t797, t798, t799, t800) = ('EXECUTE IMMEDIATE $t750', 'EXECUTE IMMEDIATE "
            + "$t751', 'EXECUTE IMMEDIATE $t752', 'EXECUTE IMMEDIATE $t753', 'EXECUTE IMMEDIATE $t754', "
            + "'EXECUTE IMMEDIATE $t755', 'EXECUTE IMMEDIATE $t756', 'EXECUTE IMMEDIATE $t757', 'EXECUTE "
            + "IMMEDIATE $t758', 'EXECUTE IMMEDIATE $t759', 'EXECUTE IMMEDIATE $t760', 'EXECUTE IMMEDIATE "
            + "$t761', 'EXECUTE IMMEDIATE $t762', 'EXECUTE IMMEDIATE $t763', 'EXECUTE IMMEDIATE $t764', "
            + "'EXECUTE IMMEDIATE $t765', 'EXECUTE IMMEDIATE $t766', 'EXECUTE IMMEDIATE $t767', 'EXECUTE "
            + "IMMEDIATE $t768', 'EXECUTE IMMEDIATE $t769', 'EXECUTE IMMEDIATE $t770', 'EXECUTE IMMEDIATE "
            + "$t771', 'EXECUTE IMMEDIATE $t772', 'EXECUTE IMMEDIATE $t773', 'EXECUTE IMMEDIATE $t774', "
            + "'EXECUTE IMMEDIATE $t775', 'EXECUTE IMMEDIATE $t776', 'EXECUTE IMMEDIATE $t777', 'EXECUTE "
            + "IMMEDIATE $t778', 'EXECUTE IMMEDIATE $t779', 'EXECUTE IMMEDIATE $t780', 'EXECUTE IMMEDIATE "
            + "$t781', 'EXECUTE IMMEDIATE $t782', 'EXECUTE IMMEDIATE $t783', 'EXECUTE IMMEDIATE $t784', "
            + "'EXECUTE IMMEDIATE $t785', 'EXECUTE IMMEDIATE $t786', 'EXECUTE IMMEDIATE $t787', 'EXECUTE "
            + "IMMEDIATE $t788', 'EXECUTE IMMEDIATE $t789', 'EXECUTE IMMEDIATE $t790', 'EXECUTE IMMEDIATE "
            + "$t791', 'EXECUTE IMMEDIATE $t792', 'EXECUTE IMMEDIATE $t793', 'EXECUTE IMMEDIATE $t794', "
            + "'EXECUTE IMMEDIATE $t795', 'EXECUTE IMMEDIATE $t796', 'EXECUTE IMMEDIATE $t797', 'EXECUTE "
            + "IMMEDIATE $t798', 'EXECUTE IMMEDIATE $t799')");
        engine.execute("SET (t801, t802, t803, t804, t805, t806, t807, t808, t809, t810, t811, t812, t813, t814, "
            + "t815, t816, t817, t818, t819, t820, t821, t822, t823, t824, t825, t826, t827, t828, t829, "
            + "t830, t831, t832, t833, t834, t835, t836, t837, t838, t839, t840, t841, t842, t843, t844, "
            + "t845, t846, t847, t848, t849, t850) = ('EXECUTE IMMEDIATE $t800', 'EXECUTE IMMEDIATE "
            + "$t801', 'EXECUTE IMMEDIATE $t802', 'EXECUTE IMMEDIATE $t803', 'EXECUTE IMMEDIATE $t804', "
            + "'EXECUTE IMMEDIATE $t805', 'EXECUTE IMMEDIATE $t806', 'EXECUTE IMMEDIATE $t807', 'EXECUTE "
            + "IMMEDIATE $t808', 'EXECUTE IMMEDIATE $t809', 'EXECUTE IMMEDIATE $t810', 'EXECUTE IMMEDIATE "
            + "$t811', 'EXECUTE IMMEDIATE $t812', 'EXECUTE IMMEDIATE $t813', 'EXECUTE IMMEDIATE $t814', "
            + "'EXECUTE IMMEDIATE $t815', 'EXECUTE IMMEDIATE $t816', 'EXECUTE IMMEDIATE $t817', 'EXECUTE "
            + "IMMEDIATE $t818', 'EXECUTE IMMEDIATE $t819', 'EXECUTE IMMEDIATE $t820', 'EXECUTE IMMEDIATE "
            + "$t821', 'EXECUTE IMMEDIATE $t822', 'EXECUTE IMMEDIATE $t823', 'EXECUTE IMMEDIATE $t824', "
            + "'EXECUTE IMMEDIATE $t825', 'EXECUTE IMMEDIATE $t826', 'EXECUTE IMMEDIATE $t827', 'EXECUTE "
            + "IMMEDIATE $t828', 'EXECUTE IMMEDIATE $t829', 'EXECUTE IMMEDIATE $t830', 'EXECUTE IMMEDIATE "
            + "$t831', 'EXECUTE IMMEDIATE $t832', 'EXECUTE IMMEDIATE $t833', 'EXECUTE IMMEDIATE $t834', "
            + "'EXECUTE IMMEDIATE $t835', 'EXECUTE IMMEDIATE $t836', 'EXECUTE IMMEDIATE $t837', 'EXECUTE "
            + "IMMEDIATE $t838', 'EXECUTE IMMEDIATE $t839', 'EXECUTE IMMEDIATE $t840', 'EXECUTE IMMEDIATE "
            + "$t841', 'EXECUTE IMMEDIATE $t842', 'EXECUTE IMMEDIATE $t843', 'EXECUTE IMMEDIATE $t844', "
            + "'EXECUTE IMMEDIATE $t845', 'EXECUTE IMMEDIATE $t846', 'EXECUTE IMMEDIATE $t847', 'EXECUTE "
            + "IMMEDIATE $t848', 'EXECUTE IMMEDIATE $t849')");
        engine.execute("SET (t851, t852, t853, t854, t855, t856, t857, t858, t859, t860, t861, t862, t863, t864, "
            + "t865, t866, t867, t868, t869, t870, t871, t872, t873, t874, t875, t876, t877, t878, t879, "
            + "t880, t881, t882, t883, t884, t885, t886, t887, t888, t889, t890, t891, t892, t893, t894, "
            + "t895, t896, t897, t898, t899, t900) = ('EXECUTE IMMEDIATE $t850', 'EXECUTE IMMEDIATE "
            + "$t851', 'EXECUTE IMMEDIATE $t852', 'EXECUTE IMMEDIATE $t853', 'EXECUTE IMMEDIATE $t854', "
            + "'EXECUTE IMMEDIATE $t855', 'EXECUTE IMMEDIATE $t856', 'EXECUTE IMMEDIATE $t857', 'EXECUTE "
            + "IMMEDIATE $t858', 'EXECUTE IMMEDIATE $t859', 'EXECUTE IMMEDIATE $t860', 'EXECUTE IMMEDIATE "
            + "$t861', 'EXECUTE IMMEDIATE $t862', 'EXECUTE IMMEDIATE $t863', 'EXECUTE IMMEDIATE $t864', "
            + "'EXECUTE IMMEDIATE $t865', 'EXECUTE IMMEDIATE $t866', 'EXECUTE IMMEDIATE $t867', 'EXECUTE "
            + "IMMEDIATE $t868', 'EXECUTE IMMEDIATE $t869', 'EXECUTE IMMEDIATE $t870', 'EXECUTE IMMEDIATE "
            + "$t871', 'EXECUTE IMMEDIATE $t872', 'EXECUTE IMMEDIATE $t873', 'EXECUTE IMMEDIATE $t874', "
            + "'EXECUTE IMMEDIATE $t875', 'EXECUTE IMMEDIATE $t876', 'EXECUTE IMMEDIATE $t877', 'EXECUTE "
            + "IMMEDIATE $t878', 'EXECUTE IMMEDIATE $t879', 'EXECUTE IMMEDIATE $t880', 'EXECUTE IMMEDIATE "
            + "$t881', 'EXECUTE IMMEDIATE $t882', 'EXECUTE IMMEDIATE $t883', 'EXECUTE IMMEDIATE $t884', "
            + "'EXECUTE IMMEDIATE $t885', 'EXECUTE IMMEDIATE $t886', 'EXECUTE IMMEDIATE $t887', 'EXECUTE "
            + "IMMEDIATE $t888', 'EXECUTE IMMEDIATE $t889', 'EXECUTE IMMEDIATE $t890', 'EXECUTE IMMEDIATE "
            + "$t891', 'EXECUTE IMMEDIATE $t892', 'EXECUTE IMMEDIATE $t893', 'EXECUTE IMMEDIATE $t894', "
            + "'EXECUTE IMMEDIATE $t895', 'EXECUTE IMMEDIATE $t896', 'EXECUTE IMMEDIATE $t897', 'EXECUTE "
            + "IMMEDIATE $t898', 'EXECUTE IMMEDIATE $t899')");
        engine.execute("SET (t901, t902, t903, t904, t905, t906, t907, t908, t909, t910, t911, t912, t913, t914, "
            + "t915, t916, t917, t918, t919, t920, t921, t922, t923, t924, t925, t926, t927, t928, t929, "
            + "t930, t931, t932, t933, t934, t935, t936, t937, t938, t939, t940, t941, t942, t943, t944, "
            + "t945, t946, t947, t948, t949, t950) = ('EXECUTE IMMEDIATE $t900', 'EXECUTE IMMEDIATE "
            + "$t901', 'EXECUTE IMMEDIATE $t902', 'EXECUTE IMMEDIATE $t903', 'EXECUTE IMMEDIATE $t904', "
            + "'EXECUTE IMMEDIATE $t905', 'EXECUTE IMMEDIATE $t906', 'EXECUTE IMMEDIATE $t907', 'EXECUTE "
            + "IMMEDIATE $t908', 'EXECUTE IMMEDIATE $t909', 'EXECUTE IMMEDIATE $t910', 'EXECUTE IMMEDIATE "
            + "$t911', 'EXECUTE IMMEDIATE $t912', 'EXECUTE IMMEDIATE $t913', 'EXECUTE IMMEDIATE $t914', "
            + "'EXECUTE IMMEDIATE $t915', 'EXECUTE IMMEDIATE $t916', 'EXECUTE IMMEDIATE $t917', 'EXECUTE "
            + "IMMEDIATE $t918', 'EXECUTE IMMEDIATE $t919', 'EXECUTE IMMEDIATE $t920', 'EXECUTE IMMEDIATE "
            + "$t921', 'EXECUTE IMMEDIATE $t922', 'EXECUTE IMMEDIATE $t923', 'EXECUTE IMMEDIATE $t924', "
            + "'EXECUTE IMMEDIATE $t925', 'EXECUTE IMMEDIATE $t926', 'EXECUTE IMMEDIATE $t927', 'EXECUTE "
            + "IMMEDIATE $t928', 'EXECUTE IMMEDIATE $t929', 'EXECUTE IMMEDIATE $t930', 'EXECUTE IMMEDIATE "
            + "$t931', 'EXECUTE IMMEDIATE $t932', 'EXECUTE IMMEDIATE $t933', 'EXECUTE IMMEDIATE $t934', "
            + "'EXECUTE IMMEDIATE $t935', 'EXECUTE IMMEDIATE $t936', 'EXECUTE IMMEDIATE $t937', 'EXECUTE "
            + "IMMEDIATE $t938', 'EXECUTE IMMEDIATE $t939', 'EXECUTE IMMEDIATE $t940', 'EXECUTE IMMEDIATE "
            + "$t941', 'EXECUTE IMMEDIATE $t942', 'EXECUTE IMMEDIATE $t943', 'EXECUTE IMMEDIATE $t944', "
            + "'EXECUTE IMMEDIATE $t945', 'EXECUTE IMMEDIATE $t946', 'EXECUTE IMMEDIATE $t947', 'EXECUTE "
            + "IMMEDIATE $t948', 'EXECUTE IMMEDIATE $t949')");
        engine.execute("SET (t951, t952, t953, t954, t955, t956, t957, t958, t959, t960, t961, t962, t963, t964, "
            + "t965, t966, t967, t968, t969, t970, t971, t972, t973, t974, t975, t976, t977, t978, t979, "
            + "t980, t981, t982, t983, t984, t985, t986, t987, t988, t989, t990, t991, t992, t993, t994, "
            + "t995, t996, t997, t998, t999, t1000) = ('EXECUTE IMMEDIATE $t950', 'EXECUTE IMMEDIATE "
            + "$t951', 'EXECUTE IMMEDIATE $t952', 'EXECUTE IMMEDIATE $t953', 'EXECUTE IMMEDIATE $t954', "
            + "'EXECUTE IMMEDIATE $t955', 'EXECUTE IMMEDIATE $t956', 'EXECUTE IMMEDIATE $t957', 'EXECUTE "
            + "IMMEDIATE $t958', 'EXECUTE IMMEDIATE $t959', 'EXECUTE IMMEDIATE $t960', 'EXECUTE IMMEDIATE "
            + "$t961', 'EXECUTE IMMEDIATE $t962', 'EXECUTE IMMEDIATE $t963', 'EXECUTE IMMEDIATE $t964', "
            + "'EXECUTE IMMEDIATE $t965', 'EXECUTE IMMEDIATE $t966', 'EXECUTE IMMEDIATE $t967', 'EXECUTE "
            + "IMMEDIATE $t968', 'EXECUTE IMMEDIATE $t969', 'EXECUTE IMMEDIATE $t970', 'EXECUTE IMMEDIATE "
            + "$t971', 'EXECUTE IMMEDIATE $t972', 'EXECUTE IMMEDIATE $t973', 'EXECUTE IMMEDIATE $t974', "
            + "'EXECUTE IMMEDIATE $t975', 'EXECUTE IMMEDIATE $t976', 'EXECUTE IMMEDIATE $t977', 'EXECUTE "
            + "IMMEDIATE $t978', 'EXECUTE IMMEDIATE $t979', 'EXECUTE IMMEDIATE $t980', 'EXECUTE IMMEDIATE "
            + "$t981', 'EXECUTE IMMEDIATE $t982', 'EXECUTE IMMEDIATE $t983', 'EXECUTE IMMEDIATE $t984', "
            + "'EXECUTE IMMEDIATE $t985', 'EXECUTE IMMEDIATE $t986', 'EXECUTE IMMEDIATE $t987', 'EXECUTE "
            + "IMMEDIATE $t988', 'EXECUTE IMMEDIATE $t989', 'EXECUTE IMMEDIATE $t990', 'EXECUTE IMMEDIATE "
            + "$t991', 'EXECUTE IMMEDIATE $t992', 'EXECUTE IMMEDIATE $t993', 'EXECUTE IMMEDIATE $t994', "
            + "'EXECUTE IMMEDIATE $t995', 'EXECUTE IMMEDIATE $t996', 'EXECUTE IMMEDIATE $t997', 'EXECUTE "
            + "IMMEDIATE $t998', 'EXECUTE IMMEDIATE $t999')");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $t131", "bottom"},
            {"EXECUTE IMMEDIATE $t200", "bottom"},
        });
    }

    @Test
    public void aBlockAmongThemCountsAsACall() {
        engine.execute("CREATE OR REPLACE TABLE nest_log (k INT, code INT, msg VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE nest_tmpl (t VARCHAR)");
        engine.execute("INSERT INTO nest_tmpl VALUES ($$DECLARE k INT DEFAULT #K#; nxt VARCHAR; BEGIN IF (k = 0) "
            + "THEN INSERT INTO nest_log VALUES (0, NULL, 'bottom'); RETURN 'bottom'; END IF; nxt := "
            + "(SELECT REPLACE(t, '#' || 'K#', (:k - 1)::VARCHAR) FROM nest_tmpl); EXECUTE IMMEDIATE :nxt; "
            + "RETURN k; EXCEPTION WHEN OTHER THEN LET c INT := SQLCODE; LET m VARCHAR := SQLERRM; INSERT "
            + "INTO nest_log VALUES (:k, :c, :m); RETURN 'caught'; END;$$)");
        assertCells(new String[][] {
            {"DECLARE nxt VARCHAR; BEGIN nxt := (SELECT REPLACE(t, '#K#', '140') FROM nest_tmpl); EXECUTE "
                + "IMMEDIATE :nxt; RETURN 'top'; END;",
                "top"},
            {"SELECT COUNT(*), MIN(k), MAX(k) FROM nest_log", "1|126|126"},
            {"SELECT k, code, LEFT(msg, 400) FROM nest_log WHERE code IS NOT NULL OR k = 0 ORDER BY k",
                "126|90235|Stored procedure execution error: Stored procedure exceeds the maximum allowed number "
                + "of recursive calls."},
        });
    }
}
