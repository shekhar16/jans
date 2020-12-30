# coding: utf-8

"""
    jans-config-api

    jans-config-api - Authorization services  # noqa: E501

    OpenAPI spec version: 1.0.0
    Contact: xxx@gluu.org
    Generated by: https://github.com/swagger-api/swagger-codegen.git
"""

import pprint
import re  # noqa: F401

import six

class SimpleCustomProperty(object):
    """NOTE: This class is auto generated by the swagger code generator program.

    Do not edit the class manually.
    """
    """
    Attributes:
      swagger_types (dict): The key is attribute name
                            and the value is attribute type.
      attribute_map (dict): The key is attribute name
                            and the value is json key in definition.
    """
    swagger_types = {
        'value1': 'str',
        'value2': 'str',
        'description': 'str'
    }

    attribute_map = {
        'value1': 'value1',
        'value2': 'value2',
        'description': 'description'
    }

    def __init__(self, value1=None, value2=None, description=None):  # noqa: E501
        """SimpleCustomProperty - a model defined in Swagger"""  # noqa: E501
        self._value1 = None
        self._value2 = None
        self._description = None
        self.discriminator = None
        if value1 is not None:
            self.value1 = value1
        if value2 is not None:
            self.value2 = value2
        if description is not None:
            self.description = description

    @property
    def value1(self):
        """Gets the value1 of this SimpleCustomProperty.  # noqa: E501


        :return: The value1 of this SimpleCustomProperty.  # noqa: E501
        :rtype: str
        """
        return self._value1

    @value1.setter
    def value1(self, value1):
        """Sets the value1 of this SimpleCustomProperty.


        :param value1: The value1 of this SimpleCustomProperty.  # noqa: E501
        :type: str
        """

        self._value1 = value1

    @property
    def value2(self):
        """Gets the value2 of this SimpleCustomProperty.  # noqa: E501


        :return: The value2 of this SimpleCustomProperty.  # noqa: E501
        :rtype: str
        """
        return self._value2

    @value2.setter
    def value2(self, value2):
        """Sets the value2 of this SimpleCustomProperty.


        :param value2: The value2 of this SimpleCustomProperty.  # noqa: E501
        :type: str
        """

        self._value2 = value2

    @property
    def description(self):
        """Gets the description of this SimpleCustomProperty.  # noqa: E501


        :return: The description of this SimpleCustomProperty.  # noqa: E501
        :rtype: str
        """
        return self._description

    @description.setter
    def description(self, description):
        """Sets the description of this SimpleCustomProperty.


        :param description: The description of this SimpleCustomProperty.  # noqa: E501
        :type: str
        """

        self._description = description

    def to_dict(self):
        """Returns the model properties as a dict"""
        result = {}

        for attr, _ in six.iteritems(self.swagger_types):
            value = getattr(self, attr)
            if isinstance(value, list):
                result[attr] = list(map(
                    lambda x: x.to_dict() if hasattr(x, "to_dict") else x,
                    value
                ))
            elif hasattr(value, "to_dict"):
                result[attr] = value.to_dict()
            elif isinstance(value, dict):
                result[attr] = dict(map(
                    lambda item: (item[0], item[1].to_dict())
                    if hasattr(item[1], "to_dict") else item,
                    value.items()
                ))
            else:
                result[attr] = value
        if issubclass(SimpleCustomProperty, dict):
            for key, value in self.items():
                result[key] = value

        return result

    def to_str(self):
        """Returns the string representation of the model"""
        return pprint.pformat(self.to_dict())

    def __repr__(self):
        """For `print` and `pprint`"""
        return self.to_str()

    def __eq__(self, other):
        """Returns true if both objects are equal"""
        if not isinstance(other, SimpleCustomProperty):
            return False

        return self.__dict__ == other.__dict__

    def __ne__(self, other):
        """Returns true if both objects are not equal"""
        return not self == other
